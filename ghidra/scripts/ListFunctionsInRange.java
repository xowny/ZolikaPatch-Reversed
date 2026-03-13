import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class ListFunctionsInRange extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 3) {
            printerr("Usage: ListFunctionsInRange <start_addr> <end_addr> <out_path>");
            return;
        }

        Address start = toAddr(args[0].startsWith("0x") ? args[0] : "0x" + args[0]);
        Address end = toAddr(args[1].startsWith("0x") ? args[1] : "0x" + args[1]);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(args[2]))) {
            FunctionIterator it = currentProgram.getFunctionManager().getFunctions(start, true);
            while (it.hasNext() && !monitor.isCancelled()) {
                Function fn = it.next();
                if (fn.getEntryPoint().compareTo(end) > 0) {
                    break;
                }
                bw.write(fn.getEntryPoint().toString());
                bw.write("\t");
                bw.write(fn.getName());
                bw.write("\t");
                bw.write(fn.getBody().getMinAddress().toString());
                bw.write("\t");
                bw.write(fn.getBody().getMaxAddress().toString());
                bw.newLine();
            }
        }

        println("Wrote function range list");
    }
}
