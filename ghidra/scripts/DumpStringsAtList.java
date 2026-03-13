import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.mem.Memory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DumpStringsAtList extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            println("Usage: DumpStringsAtList <addr_list_path> <out_path>");
            return;
        }

        Memory mem = currentProgram.getMemory();
        List<String> lines = Files.readAllLines(Paths.get(args[0]));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(args[1]))) {
            for (String line : lines) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }

                long addrLong = Long.parseUnsignedLong(s.replace("0x", ""), 16);
                Address addr = toAddr(addrLong);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 128; i++) {
                    byte b = mem.getByte(addr.add(i));
                    if (b == 0) {
                        break;
                    }
                    if (b < 0x20 || b > 0x7e) {
                        sb.append(String.format("\\x%02x", b & 0xff));
                    }
                    else {
                        sb.append((char) b);
                    }
                }
                bw.write(String.format("0x%08x\t%s\n", addrLong, sb.toString()));
            }
        }
    }
}
