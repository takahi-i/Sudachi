/*
 * Copyright (c) 2021 Works Applications Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.worksap.nlp.sudachi.dictionary;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.worksap.nlp.sudachi.WordId;

public class DictionaryPrinter {
    private final PrintStream output;
    private final GrammarImpl grammar;
    private final LexiconSet lexicon;
    private final List<String> posStrings;
    private final boolean isUser;
    private final int entrySize;

    private DictionaryPrinter(PrintStream output, BinaryDictionary dic, BinaryDictionary base) {
        this.output = output;

        if (base == null) {
            isUser = false;
            grammar = dic.getGrammar();
            lexicon = new LexiconSet(dic.getLexicon(), grammar.getSystemPartOfSpeechSize());
        } else {
            isUser = true;
            grammar = base.getGrammar();
            lexicon = new LexiconSet(base.getLexicon(), grammar.getSystemPartOfSpeechSize());

            lexicon.add(dic.getLexicon(), (short) grammar.getPartOfSpeechSize());
            if (DictionaryVersion.hasGrammar(dic.getDictionaryHeader().getVersion())) {
                grammar.addPosList(dic.getGrammar());
            }
        }

        List<String> posStrings = new ArrayList<>();
        for (short pid = 0; pid < grammar.getPartOfSpeechSize(); pid++) {
            posStrings.add(String.join(",", grammar.getPartOfSpeechString(pid)));
        }
        this.posStrings = posStrings;

        this.entrySize = dic.getLexicon().size();
    }

    private void printEntries() {
        int dic = isUser ? 1 : 0;
        for (int wordId = 0; wordId < entrySize; wordId++) {
            printEntry(WordId.make(dic, wordId));
        }
    }

    private void printEntry(int wordId) {
        short leftId = lexicon.getLeftId(wordId);
        short rightId = lexicon.getRightId(wordId);
        short cost = lexicon.getCost(wordId);
        WordInfo wordInfo = lexicon.getWordInfo(wordId);

        field(maybeEscapeString(wordInfo.getSurface()));
        field(leftId);
        field(rightId);
        field(cost);
        field(maybeEscapeString(wordInfo.getSurface()));
        field(posStrings.get(wordInfo.getPOSId()));
        field(maybeEscapeString(wordInfo.getReadingForm()));
        field(maybeEscapeString(wordInfo.getNormalizedForm()));
        field(wordIdToString(wordInfo.getDictionaryFormWordId()));
        field(getUnitType(wordInfo));
        field(splitToString(wordInfo.getAunitSplit()));
        field(splitToString(wordInfo.getBunitSplit()));
        field(splitToString(wordInfo.getWordStructure()));
        lastField(synonymIdList(wordInfo.getSynonymGoupIds()));
        output.print("\n");
    }

    void field(short value) {
        output.print(value);
        output.print(',');
    }

    void field(char value) {
        output.print(value);
        output.print(',');
    }

    void field(String value) {
        output.print(value);
        output.print(',');
    }

    void lastField(String value) {
        output.print(value);
    }

    String synonymIdList(int[] ints) {
        if (ints.length == 0) {
            return "*";
        }
        return String.join("/",
                Arrays.stream(ints).boxed().map(i -> String.format("%06d", i)).collect(Collectors.toList()));
    }

    private static boolean hasCh(String value, int ch) {
        return value.indexOf(ch) != -1;
    }

    /** escape string field of csv. */
    private String maybeEscapeString(String value) {
        boolean hasCommas = hasCh(value, ',');
        boolean hasQuotes = hasCh(value, '"');
        if (!hasCommas && !hasQuotes) {
            return value;
        }
        return unicodeEscape(value, Arrays.asList('"', ','));
    }

    /** escape specified (ascii) chars as unicode codepoint */
    private String unicodeEscape(String value, List<Character> targetChars) {
        StringBuilder sb = new StringBuilder(value.length() + 10);
        int len = value.length();
        for (int i = 0; i < len; ++i) {
            char c = value.charAt(i);
            if (targetChars.contains(c)) {
                // assume all target chars are ascii
                sb.append("\\u00").append(Integer.toHexString(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String wordIdToString(int wid) {
        return (wid < 0) ? "*" : Integer.toString(wid);
    }

    static char getUnitType(WordInfo info) {
        if (info.getAunitSplit().length == 0) {
            return 'A';
        } else if (info.getBunitSplit().length == 0) {
            return 'B';
        } else {
            return 'C';
        }
    }

    private String splitToString(int[] split) {
        if (split.length == 0) {
            return "*";
        }
        return Arrays.stream(split).mapToObj(i -> wordRef(i)).collect(Collectors.joining("/"));
    }

    private String wordRef(int wordId) {
        int dic = WordId.dic(wordId);
        int word = WordId.word(wordId);
        if (dic == 0) {
            return Integer.toString(word);
        } else {
            return "U" + Integer.toString(word);
        }
    }

    static void printDictionary(String filename, BinaryDictionary systemDict, PrintStream output) throws IOException {
        try (BinaryDictionary dictionary = new BinaryDictionary(filename)) {
            DictionaryPrinter dp;
            if (dictionary.getDictionaryHeader().isSystemDictionary()) {
                dp = new DictionaryPrinter(output, dictionary, null);
            } else if (systemDict == null) {
                throw new IllegalArgumentException(
                        "System dictionary (`-s` option) is required to print user dictionary: " + filename);
            } else {
                // user dictionary
                dp = new DictionaryPrinter(output, dictionary, systemDict);
            }
            dp.printEntries();
        }
    }

    /**
     * Prints the contents of dictionary.
     *
     * <p>
     * Usage: {@code PrintDictionary [-s file] file}
     * <p>
     * The following are the options.
     * <dl>
     * <dt>{@code -s file}</dt>
     * <dd>the system dictionary file</dd>
     * </dl>
     * <p>
     * This tool requires the system dictionary when it dumps an user dictionary.
     * 
     * @param args
     *            the option and the input filename
     * @throws IOException
     *             if IO
     */
    public static void main(String[] args) throws IOException {
        BinaryDictionary systemDict = null;

        try {
            int i = 0;
            for (i = 0; i < args.length; i++) {
                if (args[i].equals("-s") && i + 1 < args.length) {
                    systemDict = BinaryDictionary.loadSystem(args[++i]);
                } else if (args[i].equals("-h")) {
                    System.err.println("usage: PrintDictionary [-s file] file");
                    System.err.println("\t-s file\tsystem dictionary");
                    return;
                } else {
                    break;
                }
            }

            if (i < args.length) {
                printDictionary(args[i], systemDict, System.out);
            }
        } finally {
            if (systemDict != null) {
                systemDict.close();
            }
        }
    }
}
