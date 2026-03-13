import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.CodeUnit;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DumpAsmAtList extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            println("Usage: DumpAsmAtList <addr_list_path> <out_path>");
            return;
        }

        List<String> lines = Files.readAllLines(Paths.get(args[0]));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(args[1]))) {
            for (String line : lines) {
                String s = line.trim();
                if (s.isEmpty()) {
                    continue;
                }

                long addrLong = Long.parseLong(s, 16);
                Address addr = toAddr(addrLong);
                Function func = getFunctionContaining(addr);

                bw.write("### Address 0x" + String.format("%08x", addrLong) + "\n");
                if (func != null) {
                    bw.write("Function: " + func.getName() + " @ " + func.getEntryPoint() + "\n");
                }
                else {
                    bw.write("Function: <none>\n");
                }

                Instruction ins = getInstructionContaining(addr);
                if (ins == null) {
                    ins = getInstructionAt(addr);
                }
                if (ins == null) {
                    bw.write("No instruction at address.\n\n");
                    continue;
                }

                Instruction cur = ins;
                for (int i = 0; i < 8 && cur.getPrevious() != null; i++) {
                    cur = cur.getPrevious();
                }

                for (int i = 0; i < 18 && cur != null; i++) {
                    String marker = cur.getAddress().equals(ins.getAddress()) ? ">>" : "  ";
                    bw.write(marker + " " + cur.getAddress() + ": " + cur.toString() + "\n");
                    cur = cur.getNext();
                }
                bw.write("\n");
            }
        }
    }
}
