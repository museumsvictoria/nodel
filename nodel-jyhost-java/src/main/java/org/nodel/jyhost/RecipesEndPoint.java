package org.nodel.jyhost;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.joda.time.DateTime;
import org.nodel.Strings;
import org.nodel.io.Stream;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;

public class RecipesEndPoint {
    
    /**
     * This will expand in the future.
     */
    public static class RecipeInfo {
        
        @Value(name = "path", order = 1)
        public String path;        

        @Value(name = "modified", order = 2)
        public DateTime modified;
        
        @Value(name = "readme", order = 3)
        public String readme;
        
        @Value(name = "changelog", order = 4)
        public String changelog;

    }

    /**
     * Sends non-alphanumerics prefixes to bottom instead of their natural position at the top
     * to cover e.g. "(retired__" labels
     */    
    private static int specialCompare(String s1, String s2) {
        boolean nonAlpha1 = (s1.length()>0 && !Character.isLetterOrDigit(s1.charAt(0))); 
        boolean nonAlpha2 = (s2.length()>0 && !Character.isLetterOrDigit(s2.charAt(0)));
        
        // check if natural order needs to be switched
        // e.g. "(___" or "_" vs "ABCD__"
        
        return nonAlpha1 || nonAlpha2 ? Strings.compare(s2.toLowerCase(), s1.toLowerCase()) : Strings.compare(s1.toLowerCase(), s2.toLowerCase());
    }    

    /**
     * The recipes root.
     */
    private File _recipesRoot;

    /**
     * @param root Where the recipes are stored.
     */
    public RecipesEndPoint(File recipesRoot) {
        if (recipesRoot == null)
            throw new RuntimeException("Recipes root was not provided");
        
        _recipesRoot = recipesRoot;
    }

    /**
     * (see constructor)
     */
    public File getRoot() {
        return _recipesRoot;
    }    

    /**
     * Returns a list of recipe paths (sorted).
     */
    @Service(name="list", desc="Lists the available recipes, including ones already being hosted ('self/...' paths)")
    public List<RecipeInfo> list() {
        List<RecipeInfo> scriptPaths = new ArrayList<>();
        
        searchForScripts(scriptPaths, "", _recipesRoot);

        return scriptPaths;
    }
    
    /**
     * Returns a list of folders that have scripts in them e.g. 'script.py'
     * (recursive helper)
     */
    private static void searchForScripts(List<RecipeInfo> result, String path, File root) {
        File[] rootFiles = root.listFiles();

        Arrays.sort(rootFiles, new Comparator<File>() {

            @Override
            public int compare(File f1, File f2) {
                return specialCompare(f1.getName(), f2.getName());
            }

        });
        
        for (File item : rootFiles) {
            if (item.isHidden())
                continue;
            
            String name = item.getName();

            // skip specially tagged files (hidden, system, etc.)
            if (name.startsWith("_") || name.startsWith("."))
                continue;
            
            // enforce convention slashes ('/') as separators regardless of OS
            String newPath = path.length() > 0 ? path + "/" + name : name;
            
            if (item.isDirectory())
                searchForScripts(result, newPath, item);
            
            if (item.isFile() && item.getName().equalsIgnoreCase("script.py")) {
                RecipeInfo info = new RecipeInfo();
                info.modified = new DateTime(item.lastModified());
                info.path = path;

                // look for first readme
                for (File test : rootFiles) {
                    if (test.isFile() && test != item && test.getName().toLowerCase().contains("readme")) {
                        info.readme = Stream.tryReadFully(test);
                        break;
                    }
                }

                result.add(info);

                // no need to search any further
                return;
            }
            
            // otherwise skip
        }
    }

    /**
     * Returns the base directory of a given recipes specified by this path or null if it doesn't exist.
     */
    public File getRecipeFolder(String path) {
        // use OS-specific folder separator
        File result = new File(_recipesRoot, path.replace('/', File.separatorChar));
        
        return result.exists() && result.isDirectory() ? result : null;
    }

}
