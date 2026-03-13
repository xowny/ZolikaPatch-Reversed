import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.SourceType;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class RenameFunctionsFromMap extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 1) {
            println("Usage: RenameFunctionsFromMap <map_path>");
            return;
        }

        List<String> lines = Files.readAllLines(Paths.get(args[0]));
        for (String line : lines) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }

            String[] parts = s.split("\\t");
            if (parts.length < 2) {
                parts = s.split("\\s+", 2);
            }
            if (parts.length < 2) {
                continue;
            }

            long addrLong = Long.decode(parts[0]);
            String newName = parts[1].trim();
            Address addr = toAddr(addrLong);
            Function function = getFunctionAt(addr);
            if (function == null) {
                println("No function at " + parts[0]);
                continue;
            }

            function.setName(newName, SourceType.USER_DEFINED);
            println("Renamed " + parts[0] + " -> " + newName);
        }
    }
}
