import ghidra.app.script.GhidraScript;
import ghidra.program.model.data.StringDataInstance;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.DataIterator;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.symbol.Reference;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FindFunctionsForStrings extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            println("Usage: FindFunctionsForStrings <string_list_path> <out_path>");
            return;
        }

        Set<String> targets = new LinkedHashSet<>();
        List<String> lines = Files.readAllLines(Paths.get(args[0]));
        for (String line : lines) {
            String s = line.trim();
            if (!s.isEmpty()) {
                targets.add(s);
            }
        }

        Listing listing = currentProgram.getListing();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(args[1]))) {
            bw.write("target\tstring_addr\txref_from\tfunction_entry\tfunction_name\n");

            DataIterator it = listing.getDefinedData(true);
            while (it.hasNext() && !monitor.isCancelled()) {
                Data data = it.next();
                StringDataInstance sdi = StringDataInstance.getStringDataInstance(data);
                if (sdi == null) {
                    continue;
                }

                String value = sdi.getStringValue();
                if (value == null || !targets.contains(value)) {
                    continue;
                }

                Reference[] refs = getReferencesTo(data.getAddress());
                for (Reference ref : refs) {
                    Function func = getFunctionContaining(ref.getFromAddress());
                    bw.write(value);
                    bw.write("\t");
                    bw.write(data.getAddress().toString());
                    bw.write("\t");
                    bw.write(ref.getFromAddress().toString());
                    bw.write("\t");
                    bw.write(func != null ? func.getEntryPoint().toString() : "");
                    bw.write("\t");
                    bw.write(func != null ? func.getName() : "");
                    bw.write("\n");
                }
            }
        }
    }
}
