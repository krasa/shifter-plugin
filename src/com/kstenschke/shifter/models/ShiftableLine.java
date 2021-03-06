/*
 * Copyright 2011-2017 Kay Stenschke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kstenschke.shifter.models;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.kstenschke.shifter.models.shiftertypes.PhpDocParam;
import com.kstenschke.shifter.models.shiftertypes.StringHtmlEncodable;
import com.kstenschke.shifter.utils.UtilsEnvironment;
import com.kstenschke.shifter.utils.UtilsFile;
import org.jetbrains.annotations.Nullable;

/**
 * Shiftable Line
 * Shifting strategy:
 * Either line contains one word of a clearly detectable type  => shifting will transform that word.
 * Or line context can be detected                             => resp. shifting be done.
 */
public class ShiftableLine {

    private final String line;
    private final CharSequence editorText;
    private final int caretOffset;
    private final String filename;

    /**
     * Constructor
     *
     * @param document
     * @param line        Text of line
     * @param caretOffset Caret position in document
     */
    public ShiftableLine(Document document, String line, int caretOffset) {
        this.line        = line;
        this.editorText  = document.getCharsSequence();
        this.caretOffset = caretOffset;
        this.filename    = UtilsEnvironment.getDocumentFilename(document);
    }

    /**
     * Get shifted up/down word
     *
     * @param  isUp         Shift up or down?
     * @param  editor       Editor
     * @param  moreCount    Current counter while iterating multi-shift
     * @return String       Next upper/lower word
     */
    private String getShifted(boolean isUp, Editor editor, @Nullable final Integer moreCount) {
        if (UtilsFile.isPhpFile(this.filename) && PhpDocParam.isPhpDocParamLine(this.line) && !PhpDocParam.containsDataType(this.line) && PhpDocParam.containsVariableName(this.line)) {
            // Caret-line is a PHP doc @param w/o data type: guess and insert one by the variable name
            String shiftedLine = PhpDocParam.getShifted(this.line);
            if (!shiftedLine.equals(line)) {
                return shiftedLine;
            }
        }

        String[] words = this.line.trim().split("\\s+");

        // Check all words for shiftable types - shiftable if there's not more than one
        int amountShiftableWordsInSentence = 0;
        String wordShiftedTest;
        String wordUnshifted = "";
        String wordShifted   = "";
        String prefixChar    = "";
        String postfixChar   = "";

        for (String word : words) {
            if (word.length() > 2) {
                // Check if word is a hex RGB color including the #-prefix
                if (word.startsWith("#")) {
                    prefixChar = "#";
                    word = word.substring(1);
                }

                wordShiftedTest = new ShiftableWord(word, prefixChar, postfixChar, this.line, this.editorText, this.caretOffset, this.filename, moreCount).getShifted(isUp, editor);

                if (wordShiftedTest != null && !wordShiftedTest.equals(word)) {
                    amountShiftableWordsInSentence++;
                    wordUnshifted = word;
                    wordShifted = wordShiftedTest;
                }
            }
        }

        // Actual shifting
        if (amountShiftableWordsInSentence == 1) {
            // Shift detected word in line
            return this.line.replace(wordUnshifted, wordShifted);
        }

        return StringHtmlEncodable.isHtmlEncodable(this.line)
            // Encode or decode contained HTML special chars
            ? StringHtmlEncodable.getShifted(this.line)
            // No shift-ability detected, return original line
            : this.line;
    }

    /**
     * @param shiftUp
     * @param offsetLineStart
     * @param line
     * @param moreCount       Current "more" count, starting w/ 1. If non-more shift: null
     */
    public static void shiftLine(Editor editor, Integer caretOffset, boolean shiftUp, int offsetLineStart, String line, @Nullable Integer moreCount) {
        Document document = editor.getDocument();

        ShiftableLine shiftableLine = new ShiftableLine(document, line, caretOffset);

        // Replace line by shifted one
        CharSequence shiftedLine = shiftableLine.getShifted(shiftUp, editor, moreCount);
        if (shiftedLine != null) {
            document.replaceString(offsetLineStart, offsetLineStart + line.length(), shiftedLine);
        }
    }
}