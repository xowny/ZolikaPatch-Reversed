import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Data;
import ghidra.program.model.symbol.Reference;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DumpDataWordsAtList extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            println("Usage: DumpDataWordsAtList <addr_list_path> <out_path>");
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
                bw.write("### Address 0x" + String.format("%08x", addrLong) + "\n");

                Data data = getDataAt(addr);
                bw.write("Data: " + (data != null ? data.toString() : "<none>") + "\n");
                for (int i = -4; i <= 8; i++) {
                    Address cur = addr.add(i * 4L);
                    int value = getInt(cur);
                    bw.write(String.format("%s : %08x\n", cur, value));
                }

                Reference[] refs = getReferencesTo(addr);
                if (refs.length == 0) {
                    bw.write("Xrefs: <none>\n\n");
                    continue;
                }

                bw.write("Xrefs:\n");
                for (Reference ref : refs) {
                    bw.write("  " + ref.getFromAddress() + "\t" + ref.getReferenceType() + "\n");
                }
                bw.write("\n");
            }
        }
    }
}
