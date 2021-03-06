/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import gnu.trove.TObjectIntHashMap;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jul 27, 2010 4:06:27 PM
 */
public class DefaultEditorTextRepresentationHelper implements EditorTextRepresentationHelper {

  /**
   * We don't expect the user to have too many different font sizes and font types within the editor, however, need to
   * provide a defense from unlimited cache growing.
   */
  private static final int MAX_SYMBOLS_WIDTHS_CACHE_SIZE = 1000;

  /** We cache symbol widths here because it's detected to be a bottleneck. */
  private final TObjectIntHashMap<Key> mySymbolWidthCache = new TObjectIntHashMap<Key>();

  private final Key mySharedKey = new Key();

  /**
   * This is performance-related optimization because profiling shows that it's rather expensive to call
   * {@link Editor#getColorsScheme()} often due to contention in 'assert read access'.
   */
  private final Editor             myEditor;

  public DefaultEditorTextRepresentationHelper(Editor editor) {
    myEditor = editor;
  }

  @Override
  public int toVisualColumnSymbolsNumber(@NotNull CharSequence text, int start, int end, int x) {
    return EditorUtil.textWidthInColumns(myEditor, text, start, end, x);
  }

  @Override
  public int charWidth(char c, int fontType) {
    // Symbol width retrieval is detected to be a bottleneck, hence, we perform a caching here in assumption that every representation
    // helper is editor-bound and cache size is not too big.
    EditorColorsScheme colorsScheme = myEditor.getColorsScheme();
    mySharedKey.fontName = colorsScheme.getEditorFontName();
    mySharedKey.fontSize = colorsScheme.getEditorFontSize();
    mySharedKey.fontType = fontType;
    
    mySharedKey.c = c;
    return charWidth(c);
  }

  @Override
  public int textWidth(@NotNull CharSequence text, int start, int end, int fontType, int x) {
    int startToUse = start;
    for (int i = end - 1; i >= start; i--) {
      if (text.charAt(i) == '\n') {
        startToUse = i + 1;
        break;
      }
    }

    int result = 0;

    // Symbol width retrieval is detected to be a bottleneck, hence, we perform a caching here in assumption that every representation
    // helper is editor-bound and cache size is not too big.
    EditorColorsScheme colorsScheme = myEditor.getColorsScheme();
    mySharedKey.fontName = colorsScheme.getEditorFontName();
    mySharedKey.fontSize = colorsScheme.getEditorFontSize();
    mySharedKey.fontType = fontType;
    
    for (int i = startToUse; i < end; i++) {
      char c = text.charAt(i);
      if (c != '\t') {
        mySharedKey.c = c;
        result += charWidth(c);
        continue;
      }

      result += EditorUtil.nextTabStop(x + result, myEditor) - result - x;
    }
    return result;
  }

  private int charWidth(char c) {
    int result = mySymbolWidthCache.get(mySharedKey);
    if (result > 0) {
      return result;
    }
    Key key = mySharedKey.clone();
    FontInfo font = ComplementaryFontsRegistry.getFontAbleToDisplay(c, key.fontSize, key.fontType, key.fontName);
    result = font.charWidth(c);
    if (mySymbolWidthCache.size() >= MAX_SYMBOLS_WIDTHS_CACHE_SIZE) {
      // Don't expect to be here.
      mySymbolWidthCache.clear();
    }
    mySymbolWidthCache.put(key, result);
    return result;
  }

  private static class Key {
    public  String fontName;
    private int    fontSize;
    @JdkConstants.FontStyle private int    fontType;
    private char   c;

    private Key() {
      this(null, 0, 0, ' ');
    }

    Key(String fontName, int fontSize, @JdkConstants.FontStyle int fontType, char c) {
      this.fontName = fontName;
      this.fontSize = fontSize;
      this.fontType = fontType;
      this.c = c;
    }

    @Override
    protected Key clone() {
      return new Key(fontName, fontSize, fontType, c);
    }

    @Override
    public int hashCode() {
      int result = fontName != null ? fontName.hashCode() : 0;
      result = 31 * result + fontSize;
      result = 31 * result + fontType;
      result = 31 * result + c;
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Key key = (Key)o;

      if (fontSize != key.fontSize) return false;
      if (fontType != key.fontType) return false;
      if (c != key.c) return false;
      if (fontName != null ? !fontName.equals(key.fontName) : key.fontName != null) return false;

      return true;
    }
  }
}
