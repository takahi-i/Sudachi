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
import java.io.Console;

import com.worksap.nlp.sudachi.WordId;
import com.worksap.nlp.sudachi.SudachiCommandLine.FileOrStdoutPrintStream;

public class DictionaryPrinter {
    private final PrintStream output;
    private final GrammarImpl grammar;
    private final LexiconSet lexicon;
    private final List<String> posStrings;
    private final boolean isUser;
    private final int entrySize;

    DictionaryPrinter(PrintStream output, BinaryDictionary dic, BinaryDictionary base) {
        if (dic.getDictionaryHeader().isUserDictionary() && base == null) {
            throw new IllegalArgumentException("System dictionary is required to print user dictionary");
        }

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

        List<String> poss = new ArrayList<>();
        for (short pid = 0; pid < grammar.getPartOfSpeechSize(); pid++) {
            poss.add(String.join(",", grammar.getPartOfSpeechString(pid)));
        }
        this.posStrings = poss;

        this.entrySize = dic.getLexicon().size();
    }

    private static void printUsage() {
        Console console = System.console();
        console.printf("usage: PrintDictionary [-o file] [-s file] file\n");
        console.printf("\t-o file\toutput to file\n");
        console.printf("\t-s file\tsystem dictionary\n");
    }

    void printEntries() {
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
        field(wordRefToString(wordInfo.getDictionaryFormWordId()));
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

    String wordRefToString(int wid) {
        if (wid < 0) {
            return "*";
        }
        return "\"" + wordRef(wid) + "\"";
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
        return "\"" + Arrays.stream(split).mapToObj(this::wordRef).collect(Collectors.joining("/")) + "\"";
    }

    private String wordRef(int wordId) {
        WordInfo info = lexicon.getWordInfo(wordId);
        String surface = maybeEscapeString(info.getSurface());
        short posId = info.getPOSId();
        String pos = grammar.getPartOfSpeechString(posId).toString();
        String reading = maybeEscapeString(info.getReadingForm());
        return String.format("%s,%s,%s", surface, pos, reading);
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
        String systemDictPath = null;
        String outputFileName = null;

        int i = 0;
        for (i = 0; i < args.length; i++) {
            if (args[i].equals("-h")) {
                printUsage();
                return;
            } else if (args[i].equals("-o") && i + 1 < args.length) {
                outputFileName = args[++i];
            } else if (args[i].equals("-s") && i + 1 < args.length) {
                systemDictPath = args[++i];
            } else {
                break;
            }
        }
        if (i >= args.length) {
            System.console().printf("target dictionary file is missing");
            return;
        }

        String dictPath = args[i];
        BinaryDictionary systemDict = null;
        try (BinaryDictionary dict = new BinaryDictionary(dictPath);
                PrintStream output = outputFileName == null ? new FileOrStdoutPrintStream()
                        : new FileOrStdoutPrintStream(outputFileName);) {
            if (systemDictPath != null) {
                systemDict = BinaryDictionary.loadSystem(systemDictPath);
            }

            DictionaryPrinter printer = new DictionaryPrinter(output, dict, systemDict);
            printer.printEntries();
        } finally {
            if (systemDict != null) {
                systemDict.close();
            }
        }
    }
}
