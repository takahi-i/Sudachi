/*
 * Copyright (c) 2017-2022 Works Applications Co., Ltd.
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.worksap.nlp.sudachi.TestDictionary;
import com.worksap.nlp.sudachi.Utils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DictionaryPrinterTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        TestDictionary td = TestDictionary.INSTANCE;
        Path folder = temporaryFolder.getRoot().toPath();
        td.getSystemDictData().writeData(folder.resolve("system.dic"));
        td.getUserDict1Data().writeData(folder.resolve("user.dic"));
        Utils.copyResource(folder, "/unk.def", "/dict/matrix.def");
    }

    private String wordInfoToString(int wordId, WordInfo wordInfo) {
        return String.format("%d, %s, %d, %d, %s, %d, %s, %s, %s, %s, %s, %s", wordId, wordInfo.getSurface(),
                wordInfo.getLength(), wordInfo.getPOSId(), wordInfo.getNormalizedForm(),
                wordInfo.getDictionaryFormWordId(), wordInfo.getDictionaryForm(), wordInfo.getReadingForm(),
                Arrays.toString(wordInfo.getAunitSplit()), Arrays.toString(wordInfo.getBunitSplit()),
                Arrays.toString(wordInfo.getWordStructure()), Arrays.toString(wordInfo.getSynonymGoupIds()));
    }

    @Test
    public void printWithSystemDict() throws IOException {
        File inputFile = new File(temporaryFolder.getRoot(), "system.dic");
        String[] actuals;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(output);
                BinaryDictionary dict = new BinaryDictionary(inputFile.getPath())) {
            DictionaryPrinter printer = new DictionaryPrinter(ps, dict, null);
            printer.printEntries();
            actuals = output.toString().split(System.lineSeparator());
        }
        assertThat(actuals.length, is(40));
        assertThat(actuals[0], is("た,1,1,8729,た,助動詞,*,*,*,助動詞-タ,終止形-一般,タ,た,*,A,*,*,*,*"));
    }

    @Test
    public void printWithUserDict() throws IOException {
        File inputFile = new File(temporaryFolder.getRoot(), "user.dic");
        File systemDictFile = new File(temporaryFolder.getRoot(), "system.dic");
        try (BinaryDictionary systemDict = BinaryDictionary.loadSystem(systemDictFile.getPath())) {
            String[] actuals;
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                    PrintStream ps = new PrintStream(output);
                    BinaryDictionary dict = new BinaryDictionary(inputFile.getPath())) {
                DictionaryPrinter printer = new DictionaryPrinter(ps, dict, systemDict);
                printer.printEntries();
                actuals = output.toString().split(System.lineSeparator());
            }
            assertThat(actuals.length, is(4));
            assertThat(actuals[2], is("東京府,6,6,2816,東京府,名詞,固有名詞,地名,一般,*,*,トウキョウフ,東京府,*,B,5/U1,*,5/U1,000001/000003"));
            assertThat(actuals[3], is("すだち,6,6,2816,すだち,被子植物門,双子葉植物綱,ムクロジ目,ミカン科,ミカン属,スダチ,スダチ,すだち,*,A,*,*,*,*"));
        }
    }

    @Test
    public void printUnescape() throws IOException {
        File lexFile = temporaryFolder.newFile();
        try (FileWriter writer = new FileWriter(lexFile)) {
            writer.write("\\u002c,0,0,1000,\\u002c,補助記号,読点,*,*,*,*,\\u002C,、,*,A,*,*,*,*\n");
        }

        String matrixFilePath = new File(temporaryFolder.getRoot(), "matrix.def").getPath();
        File dictFile = temporaryFolder.newFile();
        DictionaryBuilder
                .main(new String[] { "-o", dictFile.getPath(), "-m", matrixFilePath, "-d", "test", lexFile.getPath() });

        String[] actuals;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(output);
                BinaryDictionary dict = new BinaryDictionary(dictFile.getPath())) {
            DictionaryPrinter printer = new DictionaryPrinter(ps, dict, null);
            printer.printEntries();
            actuals = output.toString().split(System.lineSeparator());
        }
        assertThat(actuals.length, is(1));
        assertThat(actuals[0], is("\\u002c,0,0,1000,\\u002c,補助記号,読点,*,*,*,*,\\u002c,、,*,A,*,*,*,*"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void printWithUserDictWithoutGrammar() throws IOException {
        File inputFile = new File(temporaryFolder.getRoot(), "user.dic");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(output);
                BinaryDictionary dict = new BinaryDictionary(inputFile.getPath())) {
            DictionaryPrinter printer = new DictionaryPrinter(ps, dict, null);
            printer.printEntries();
        }
    }

    @Test(expected = IOException.class)
    public void readGrammarWithInvalidFile() throws IOException {
        File inputFile = new File(temporaryFolder.getRoot(), "unk.def");
        BinaryDictionary.loadSystem(inputFile.getPath());
    }

    @Test
    public void rebuildSystem() throws IOException {
        File inputFile = new File(temporaryFolder.getRoot(), "system.dic");

        String printed;
        BinaryDictionary original = new BinaryDictionary(inputFile.getPath());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(output)) {
            DictionaryPrinter printer = new DictionaryPrinter(ps, original, null);
            printer.printEntries();
            printed = output.toString();
        }

        File lexiconFile = new File(temporaryFolder.getRoot(), "system_lex.csv");
        FileOutputStream os = new FileOutputStream(lexiconFile);
        os.write(printed.getBytes());
        os.close();

        File rebuiltDict = new File(temporaryFolder.getRoot(), "system.dic2");
        File matrixFile = new File(temporaryFolder.getRoot(), "matrix.def");
        DictionaryBuilder.main(new String[] { "-o", rebuiltDict.getPath(), "-m", matrixFile.getPath(), "-d",
                "rebuild system dict", lexiconFile.getPath() });

        BinaryDictionary rebuilt = new BinaryDictionary(rebuiltDict.getPath());
        Long version = original.getDictionaryHeader().getVersion();
        assertThat(rebuilt.getDictionaryHeader().getVersion(), is(version));

        if (DictionaryVersion.hasGrammar(version)) {
            GrammarImpl grammarO = original.getGrammar();
            GrammarImpl grammarR = rebuilt.getGrammar();
            int originalPosSize = grammarO.getPartOfSpeechSize();
            assertThat(grammarR.getPartOfSpeechSize(), is(originalPosSize));
            for (short i = 0; i < originalPosSize; i++) {
                assertThat(grammarR.getPartOfSpeechString(i), is(grammarO.getPartOfSpeechString(i)));
            }
        }

        DoubleArrayLexicon lexO = original.getLexicon();
        DoubleArrayLexicon lexR = rebuilt.getLexicon();
        int wordSize = lexO.size();
        assertThat(lexR.size(), is(wordSize));
        for (int i = 0; i < wordSize; i++) {
            WordInfo wio = lexO.getWordInfo(i);
            WordInfo wir = lexR.getWordInfo(i);
            assertThat(wordInfoToString(i, wir), is(wordInfoToString(i, wio)));
        }

        original.close();
        rebuilt.close();
    }

    @Test
    public void rebuildUser() throws IOException {
        File inputFile = new File(temporaryFolder.getRoot(), "user.dic");
        File systemDictFile = new File(temporaryFolder.getRoot(), "system.dic");

        String printed;
        BinaryDictionary systemDict = BinaryDictionary.loadSystem(systemDictFile.getPath());
        BinaryDictionary original = new BinaryDictionary(inputFile.getPath());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(output)) {
            DictionaryPrinter printer = new DictionaryPrinter(ps, original, systemDict);
            printer.printEntries();
            printed = output.toString();
        }

        File lexiconFile = new File(temporaryFolder.getRoot(), "user_lex.csv");
        FileOutputStream os = new FileOutputStream(lexiconFile);
        os.write(printed.getBytes());
        os.close();

        File rebuiltDict = new File(temporaryFolder.getRoot(), "user.dic2");
        UserDictionaryBuilder.main(new String[] { "-o", rebuiltDict.getPath(), "-s", systemDictFile.getPath(), "-d",
                "rebuild user dict", lexiconFile.getPath() });

        BinaryDictionary rebuilt = new BinaryDictionary(rebuiltDict.getPath());
        Long version = original.getDictionaryHeader().getVersion();
        assertThat(rebuilt.getDictionaryHeader().getVersion(), is(version));

        if (DictionaryVersion.hasGrammar(version)) {
            GrammarImpl grammarO = original.getGrammar();
            GrammarImpl grammarR = rebuilt.getGrammar();
            int originalPosSize = grammarO.getPartOfSpeechSize();
            assertThat(grammarR.getPartOfSpeechSize(), is(originalPosSize));
            for (short i = 0; i < originalPosSize; i++) {
                assertThat(grammarR.getPartOfSpeechString(i), is(grammarO.getPartOfSpeechString(i)));
            }
        }

        DoubleArrayLexicon lexO = original.getLexicon();
        DoubleArrayLexicon lexR = rebuilt.getLexicon();
        int wordSize = lexO.size();
        assertThat(lexR.size(), is(wordSize));
        for (int i = 0; i < wordSize; i++) {
            WordInfo wio = lexO.getWordInfo(i);
            WordInfo wir = lexR.getWordInfo(i);
            assertThat(wordInfoToString(i, wir), is(wordInfoToString(i, wio)));
        }

        original.close();
        rebuilt.close();
        systemDict.close();
    }

    @Test
    public void commandLineUser() throws IOException {
        String systemDictPath = new File(temporaryFolder.getRoot(), "system.dic").getPath();
        String userDictPath = new File(temporaryFolder.getRoot(), "user.dic").getPath();
        String outputFileName = temporaryFolder.newFile().getPath();

        DictionaryPrinter.main(new String[] { "-o", outputFileName, "-s", systemDictPath, userDictPath });

        List<String> lines = Files.lines(Paths.get(outputFileName)).collect(Collectors.toList());
        assertThat(lines.size(), is(4));
        assertThat(lines.get(2), is("東京府,6,6,2816,東京府,名詞,固有名詞,地名,一般,*,*,トウキョウフ,東京府,*,B,5/U1,*,5/U1,000001/000003"));
        assertThat(lines.get(3), is("すだち,6,6,2816,すだち,被子植物門,双子葉植物綱,ムクロジ目,ミカン科,ミカン属,スダチ,スダチ,すだち,*,A,*,*,*,*"));
    }
}