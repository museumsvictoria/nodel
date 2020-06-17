package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.nodel.core.Nodel;

/**
 * Widely used across the framework as Node names and binding points. Represents a simplified Nodel name that
 * can be used for map keys and lists, for case-and-punctuation-insensitive comparisons.
 */
public class SimpleName {

    /**
     * (see 'getOriginalName')
     */
    private String _original;
    
    /**
     * (see 'getReducedName')
     */
    private String _reduced;
    
    /**
     * For matching purposed.
     */
    private String _reducedForMatching;
    
    /**
     * (constructor)
     */
    public SimpleName(String original) {
        _original = original;
        _reduced = Nodel.reduce(original);
        _reducedForMatching = SimpleName.flatten(_reduced);
    }
     
    /**
     * (reserved for further handling)
     */
    public static SimpleName intoSimple(Object obj) {
        if (obj instanceof SimpleName)
            return (SimpleName) obj;
        
        else if (obj instanceof String)
            return new SimpleName((String) obj);
        
        else if (obj != null)
            return new SimpleName(obj.toString());
        
        else
            return null;
    }
    
    /**
     * Returns the original name used.
     */
    public String getOriginalName() {
        return _original;
    }
    
    /**
     * Returns the reduced name.
     */
    public String getReducedName() {
        return _reduced;
    }
    
    /**
     * Gets the reduced name for string matching names.
     */
    public String getReducedForMatchingName() {
        return _reducedForMatching;
    }
    
    /**
     * Allow comparison of strings too.
     */
    @Override
    public boolean equals(Object obj) {
        SimpleName other;
        
        if (obj instanceof String)
            other = new SimpleName((String) obj);
        
        else if (obj instanceof SimpleName)
            other = (SimpleName) obj;
        
        else if (obj == null)
            return false;
        
        else
            other = new SimpleName(obj.toString());

        return _reducedForMatching.equals(other._reducedForMatching);
    }
    
    @Override
    public int hashCode() {
        return _reducedForMatching.hashCode();
    } // (method)
    
    /**
     * Returns the reduced version of the name. 
     */
    public String toString() {
        return _original;
    }
    
    /**
     * Returns a string array of original versions.
     */
    public static String[] intoOriginals(List<SimpleName> list) {
        int len = list.size();
        String[] names = new String[len];

        for (int i = 0; i < len; i++)
            names[i] = list.get(i).getOriginalName();

        return names;
    }
    
    /**
     * Returns a string array of reduced versions.
     */
    public static String[] intoReduced(List<SimpleName> list) {
        int len = list.size();;
        String[] names = new String[len];
        
        for(int i=0; i<len; i++)
            names[i] = list.get(i).getReducedName();
        
        return names; 
    }
    
    /**
     * Returns a NodelName list from an array of names.
     */
    public static List<SimpleName> fromNames(String[] names) {
        int len = names.length;

        List<SimpleName> nodes = new ArrayList<SimpleName>(len);

        for (String name : names)
            nodes.add(new SimpleName(name));

        return nodes;
    } // (method)
    
    private final static char[] GLOB_CHARS = "*?".toCharArray();
    
    /**
     * Can be used in conjunction with 'wildcardMatch' for efficiency.
     */
    public static String[] wildcardMatchTokens(String wildcardMatcher) {
        return splitOnTokens(Nodel.reduceToLower(wildcardMatcher, GLOB_CHARS));
    }

    /**
    * Checks a name to see if it matches the specified wildcard matcher. The wildcard matcher 
    * uses the characters '?' and '*' to represent a single or multiple wildcard characters.
    * This is the same as often found on Dos/Unix command lines.
    * 
    * TODO: check licensing requirements (FilenameUtils taken from package 'org.apache.commons.io')
    */    
    public static boolean wildcardMatch(SimpleName name, String wildcardMatcher) {
        if (name == null && wildcardMatcher == null)
            return true;

        if (name == null || wildcardMatcher == null)
            return false;

        return wildcardMatch(name, splitOnTokens(Nodel.reduceToLower(wildcardMatcher, GLOB_CHARS)));
    }

    /**
     * (Overloaded) Can provide pre-created simple name and tokens for efficiency.
     */
    public static boolean wildcardMatch(SimpleName name, String[] tokens) {
        if (name == null && tokens == null)
            return true;

        if (name == null || tokens == null)
            return false;

        String reducedName = name.getReducedForMatchingName();

        boolean anyChars = false;
        int textIdx = 0;
        int wcsIdx = 0;
        Stack<int[]> backtrack = new Stack<int[]>();

        // loop around a backtrack stack, to handle complex * matching
        do {
            if (backtrack.size() > 0) {
                int[] array = backtrack.pop();
                wcsIdx = array[0];
                textIdx = array[1];
                anyChars = true;
            }

            // loop whilst tokens and text left to process
            while (wcsIdx < tokens.length) {

                if (tokens[wcsIdx].equals("?")) {
                    // ? so move to next text char
                    textIdx++;
                    anyChars = false;

                } else if (tokens[wcsIdx].equals("*")) {
                    // set any chars status
                    anyChars = true;
                    if (wcsIdx == tokens.length - 1) {
                        textIdx = reducedName.length();
                    }

                } else {
                    // matching text token
                    if (anyChars) {
                        // any chars then try to locate text token
                        textIdx = reducedName.indexOf(tokens[wcsIdx], textIdx);
                        if (textIdx == -1) {
                            // token not found
                            break;
                        }
                        int repeat = reducedName.indexOf(tokens[wcsIdx], textIdx + 1);
                        if (repeat >= 0) {
                            backtrack.push(new int[] { wcsIdx, repeat });
                        }
                    } else {
                        // matching from current position
                        if (!reducedName.startsWith(tokens[wcsIdx], textIdx)) {
                            // couldnt match token
                            break;
                        }
                    }

                    // matched text token, move text index to end of matched token
                    textIdx += tokens[wcsIdx].length();
                    anyChars = false;
                }

                wcsIdx++;
            }

            // full match
            if (wcsIdx == tokens.length && textIdx == reducedName.length()) {
                return true;
            }

        } while (backtrack.size() > 0);

        return false;
    }

   /**
    * Splits a string into a number of tokens.
    * 
    * @param text  the text to split
    * @return the tokens, never null
    */
    private static String[] splitOnTokens(String text) {
        // used by wildcardMatch

        if (text.indexOf("?") == -1 && text.indexOf("*") == -1) {
            return new String[] { text };
        }

        char[] array = text.toCharArray();
        ArrayList<String> list = new ArrayList<String>();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (array[i] == '?' || array[i] == '*') {
                if (buffer.length() != 0) {
                    list.add(buffer.toString());
                    buffer.setLength(0);
                }
                if (array[i] == '?') {
                    list.add("?");
                } else if (list.size() == 0 || (i > 0 && list.get(list.size() - 1).equals("*") == false)) {
                    list.add("*");
                }
            } else {
                buffer.append(array[i]);
            }
        }
        if (buffer.length() != 0) {
            list.add(buffer.toString());
        }

        return list.toArray(new String[list.size()]);
    }
    
    /**
     * Holds the list of diacritics with associated ASCII "equivalent" (case sensitive)
     * (kindly adopted from stackoverflow.com/questions/249087/how-do-i-remove-diacritics-accents-from-a-string-in-net)
     */
    private final static String[][] DIACRITICS_TOASCII_WITHCASE = {
            { "äæǽ", "ae" }, { "öœ", "oe" }, { "ü", "ue" }, { "Ä", "Ae" }, { "Ü", "Ue" }, { "Ö", "Oe" },
            { "ÀÁÂÃÄÅǺĀĂĄǍΑΆẢẠẦẪẨẬẰẮẴẲẶА", "A" }, { "àáâãåǻāăąǎªαάảạầấẫẩậằắẵẳặа", "a" },
            { "Б", "B" }, { "б", "b" }, { "ÇĆĈĊČ", "C" }, { "çćĉċč", "c" }, { "Д", "D" },
            { "д", "d" }, { "ÐĎĐΔ", "Dj" }, { "ðďđδ", "dj" }, { "ÈÉÊËĒĔĖĘĚΕΈẼẺẸỀẾỄỂỆЕЭ", "E" }, { "èéêëēĕėęěέεẽẻẹềếễểệеэ", "e" },
            { "Ф", "F" }, { "ф", "f" }, { "ĜĞĠĢΓГҐ", "G" }, { "ĝğġģγгґ", "g" }, { "ĤĦ", "H" }, { "ĥħ", "h" }, { "ÌÍÎÏĨĪĬǏĮİΗΉΊΙΪỈỊИЫ", "I" },
            { "ìíîïĩīĭǐįıηήίιϊỉịиыї", "i" }, { "Ĵ", "J" }, { "ĵ", "j" }, { "ĶΚК", "K" }, { "ķκк", "k" }, { "ĹĻĽĿŁΛЛ", "L" },
            { "ĺļľŀłλл", "l" }, { "М", "M" }, { "м", "m" }, { "ÑŃŅŇΝН", "N" }, { "ñńņňŉνн", "n" },
            { "ÒÓÔÕŌŎǑŐƠØǾΟΌΩΏỎỌỒỐỖỔỘỜỚỠỞỢО", "O" }, { "òóôõōŏǒőơøǿºοόωώỏọồốỗổộờớỡởợо", "o" }, { "П", "P" }, { "п", "p" },
            { "ŔŖŘΡР", "R" }, { "ŕŗřρр", "r" }, { "ŚŜŞȘŠΣС", "S" }, { "śŝşșšſσςс", "s" }, { "ȚŢŤŦτТ", "T" }, { "țţťŧт", "t" }, { "ÙÚÛŨŪŬŮŰŲƯǓǕǗǙǛŨỦỤỪỨỮỬỰУ", "U" },
            { "ùúûũūŭůűųưǔǖǘǚǜυύϋủụừứữửựу", "u" }, { "ÝŸŶΥΎΫỲỸỶỴЙ", "Y" }, { "ýÿŷỳỹỷỵй", "y" }, { "В", "V" }, { "в", "v" }, { "Ŵ", "W" }, { "ŵ", "w" },
            { "ŹŻŽΖЗ", "Z" }, { "źżžζз", "z" }, { "ÆǼ", "AE" }, { "ß", "ss" }, { "Ĳ", "IJ" }, { "ĳ", "ij" }, { "Œ", "OE" }, { "ƒ", "f" },
            { "ξ", "ks" }, { "π", "p" }, { "β", "v" }, { "μ", "m" }, { "ψ", "ps" }, { "Ё", "Yo" }, { "ё", "yo" }, { "Є", "Ye" },
            { "є", "ye" }, { "Ї", "Yi" }, { "Ж", "Zh" }, { "ж", "zh" }, { "Х", "Kh" }, { "х", "kh" }, { "Ц", "Ts" }, { "ц", "ts" },
            { "Ч", "Ch" }, { "ч", "ch" }, { "Ш", "Sh" }, { "ш", "sh" }, { "Щ", "Shch" }, { "щ", "shch" }, { "ЪъЬь", "" }, { "Ю", "Yu" }, { "ю", "yu" },
            { "Я", "Ya" }, { "я", "ya" } };
    
    /**
     * Initialised for quick runtime use. e.g. 'Crème Brûlée' -> 'creme brulee'
     */
    private static final Map<Character, String> s_diacritic_tolowerascii = new HashMap<>();
    
    static {
        initDiacriticMap();
    }
    
    private static void initDiacriticMap() {
        for (String[] entry : DIACRITICS_TOASCII_WITHCASE) {
            String diacritics = entry[0];
            String lowerAscii = entry[1].toLowerCase();
            
            int len = diacritics.length();
            
            for (int a=0; a<len; a++) {
                char diacritic = diacritics.charAt(a);
                
                s_diacritic_tolowerascii.put(diacritic, lowerAscii);
            }
        }
    }
    
    /**
     * Flattens for loose name matching
     * 
     * "Crème Brûlée" -> "cremebrulee"  
     */    
    public static String flatten(String name) {
        return flatten(name, null);
    }    
    
    /**
     * (see 'flatten', with characters to passthrough)  
     */
    public static String flatten(String name, char[] passthrough) {
        int len = name.length();
        StringBuilder sb = new StringBuilder();
        
        for (int a = 0; a < len; a++) {
            char c = name.charAt(a);
            
            // deal with "pass through" first
            if (passthrough != null && isPresent(c, passthrough)) {
                String flatDialect = s_diacritic_tolowerascii.get(c);
                if (flatDialect != null)
                    sb.append(flatDialect);
                else
                    sb.append(Character.toLowerCase(c));
            }
            
            // skip spaces and most ASCII control codes
            else if (c <= 32) {
                continue;
            }
            
            // else incl. letters or digits and everything else non-7bit-ASCII that is not a space
            else if (Character.isLetterOrDigit(c) || (c > 127 && !Character.isSpaceChar(c))) {
                String flatDialect = s_diacritic_tolowerascii.get(c);
                if (flatDialect != null)
                    sb.append(flatDialect);
                else
                    sb.append(Character.toLowerCase(c));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Returns true if a character is present in a list of them (held within a string)
     * ('chars' must be pre-checked)
     * (convenience method)
     */
    public static boolean isPresent(char c, char[] chars) {
        int len = chars.length;
        for (int a = 0; a < len; a++) {
            if (chars[a] == c)
                return true;
        }
        return false;
    }
    
    /**
     * Flatten a char and appends into a string builder.
     */
    public static void flattenChar(char c, StringBuilder sb) {
        // skip spaces and most ASCII control codes
        if (c <= 32)
            return;
        
        // include letters or digits and everything else non-7-bit-ASCII that is not a space
        if (Character.isLetterOrDigit(c) || (c > 127 && !Character.isSpaceChar(c))) {
            String flatDialect = s_diacritic_tolowerascii.get(c);
            if (flatDialect != null)
                sb.append(flatDialect);
            else
                sb.append(Character.toLowerCase(c));
        }
        
        // ...skip remaining
    }
    
} // (class)
