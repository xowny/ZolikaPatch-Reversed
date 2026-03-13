import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileOptions;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DecompileFunctionsFromList extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            println("Usage: DecompileFunctionsFromList <addr_list_path> <out_path>");
            return;
        }

        DecompInterface ifc = new DecompInterface();
        DecompileOptions options = new DecompileOptions();
        ifc.setOptions(options);
        ifc.toggleCCode(true);
        ifc.toggleSyntaxTree(false);
        ifc.setSimplificationStyle("decompile");
        if (!ifc.openProgram(currentProgram)) {
            printerr("Failed to open program in decompiler");
            return;
        }

        List<String> lines = Files.readAllLines(Paths.get(args[0]));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(args[1]))) {
            for (String line : lines) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }

                long addrLong = Long.parseUnsignedLong(s.replace("0x", ""), 16);
                Address addr = toAddr(addrLong);
                Function fn = getFunctionContaining(addr);

                bw.write("### Address 0x" + String.format("%08x", addrLong) + "\n");
                if (fn == null) {
                    bw.write("Function: <none>\n\n");
                    continue;
                }

                bw.write("Function: " + fn.getName() + " @ " + fn.getEntryPoint() + "\n");
                DecompileResults res = ifc.decompileFunction(fn, 60, monitor);
                if (!res.decompileCompleted()) {
                    bw.write("<decompile failed>\n\n");
                    continue;
                }
                bw.write(res.getDecompiledFunction().getC());
                bw.write("\n\n");
            }
        }

        ifc.dispose();
        println("Wrote decompile report to " + args[1]);
    }
}
