package org.nodel.jyhost;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map.Entry;

import org.nodel.SimpleName;
import org.nodel.host.Binding;
import org.nodel.host.LocalBindings;
import org.nodel.host.ParameterBinding;
import org.nodel.host.ParameterBindings;
import org.nodel.nodelhost.Launch;
import org.nodel.reflection.Serialisation;

public class ExampleScript {
    
    /**
     * Generate an example script file.
     */
    public static String generateExampleScript() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        boolean first = true;
        
        // embed version info
        pw.format("# Nodel auto-generated example Python script that applies to version v%s or later.%n", Launch.VERSION);
        pw.println("'''This node demonstrates a simple PyNode.'''");

        // don't use a dynamic time-stamp otherwise it'll keep on updating...
        // pw.println("# Created " + DateTime.now().toString(DateTimeFormat.longDateTime()));
        
        pw.println();
        
        pw.println("# Local actions this Node provides");
        if (LocalBindings.Example.actions.size() == 0)
            pw.println("# (none)");
        
        for (Entry<SimpleName, Binding> entry : LocalBindings.Example.actions.entrySet()) {
            SimpleName name = entry.getKey();
            Binding binding = entry.getValue();
            
            if (!first)
                pw.println();
            else
                first = false;
            
            pw.format("def local_action_%s(arg = None):%n", name.getReducedName());
            pw.format("  \"\"\"%s\"\"\"%n", Serialisation.serialise(binding));
            pw.format("  print 'Action %s requested'%n", name.getReducedName());
        } // (for)

        pw.println();

        first = true;
        pw.println("# Local events this Node provides");
        if (LocalBindings.Example.events.size() == 0)
            pw.println("# (none)");

        for (Entry<SimpleName, Binding> entry : LocalBindings.Example.events.entrySet()) {
            SimpleName name = entry.getKey();
            Binding binding = entry.getValue();
            
            if (!first)
                pw.println();
            else {
                first = false;
            }
            pw.format("local_event_%s = LocalEvent('%s')%n", name.getReducedName(), Serialisation.serialise(binding));
            pw.format("# local_event_%s.emit(arg)%n", name.getReducedName());
        }
        
        pw.println();
        
        pw.println("# Remote actions this Node requires");
        pw.format("remote_action_%s = RemoteAction('%s')%n", Binding.RemoteActionExampleName.getReducedName(), Serialisation.serialise(Binding.RemoteActionExample));
        pw.format("# remote_action_%s.call(arg)%n", Binding.RemoteActionExampleName.getReducedName());
        
        pw.println();

        pw.println("# Remote events this Node requires");
        pw.format("def remote_event_%s(arg = None):%n", Binding.RemoteEventExampleName.getReducedName());
        pw.format("  \"\"\"%s\"\"\"%n", Serialisation.serialise(Binding.RemoteEventExample));
        pw.format("  print 'Remote event %s arrived'%n", Binding.RemoteEventExampleName.getReducedName());
        
        pw.println();
        
        first = true;
        pw.println("# Parameters used by this Node");
        if (ParameterBindings.Example.size() == 0)
            pw.println("# (none)");
        
        for(Entry<SimpleName, ParameterBinding> entry : ParameterBindings.Example.entrySet()) {
            if (!first)
                pw.println();
            else
                first = false;
            
            String paramName = entry.getKey().getReducedName();
            
            pw.format("param_%s = Parameter('%s')%n", paramName, Serialisation.serialise(entry.getValue()));
        }
        
        pw.println();
        
        pw.println("def main(arg = None):");
        pw.println("  # Start your script here.");
        pw.println("  print 'Nodel script started.'");
        pw.println();
        
        return sw.toString();
    } // (method)
    

}
