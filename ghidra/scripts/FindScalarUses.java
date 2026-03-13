import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.DataIterator;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.scalar.Scalar;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FindScalarUses extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            println("Usage: FindScalarUses <addr_list_path> <out_path>");
            return;
        }

        List<String> lines = Files.readAllLines(Paths.get(args[0]));
        Map<Long, String> targets = new LinkedHashMap<>();
        for (String line : lines) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            String[] parts = s.split("\\s+", 2);
            long value = Long.parseUnsignedLong(parts[0].replace("0x", ""), 16);
            String label = parts.length > 1 ? parts[1].trim() : "";
            targets.put(value, label);
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(args[1]))) {
            for (Map.Entry<Long, String> target : targets.entrySet()) {
                long value = target.getKey();
                String label = target.getValue();
                bw.write(String.format("### 0x%08x", value));
                if (!label.isEmpty()) {
                    bw.write(" " + label);
                }
                bw.write("\n");

                List<String> hits = new ArrayList<>();

                Instruction ins = getFirstInstruction();
                while (ins != null && !monitor.isCancelled()) {
                    Object[] objs = ins.getOpObjects(0);
                    for (int op = 0; op < ins.getNumOperands(); op++) {
                        objs = ins.getOpObjects(op);
                        for (Object obj : objs) {
                            if (obj instanceof Scalar) {
                                Scalar scalar = (Scalar) obj;
                                long unsigned = scalar.getUnsignedValue();
                                if ((unsigned & 0xffffffffL) == (value & 0xffffffffL)) {
                                    Function fn = getFunctionContaining(ins.getAddress());
                                    hits.add(String.format(
                                        "INS\t%s\t%s\t%s",
                                        ins.getAddress(),
                                        fn != null ? fn.getName() : "<no_function>",
                                        ins.toString()
                                    ));
                                }
                            }
                        }
                    }
                    ins = ins.getNext();
                }

                DataIterator it = currentProgram.getListing().getDefinedData(true);
                while (it.hasNext() && !monitor.isCancelled()) {
                    Data data = it.next();
                    Object valueObj = data.getValue();
                    if (valueObj instanceof Scalar) {
                        Scalar scalar = (Scalar) valueObj;
                        long unsigned = scalar.getUnsignedValue();
                        if ((unsigned & 0xffffffffL) == (value & 0xffffffffL)) {
                            Function fn = getFunctionContaining(data.getAddress());
                            hits.add(String.format(
                                "DATA\t%s\t%s\t%s",
                                data.getAddress(),
                                fn != null ? fn.getName() : "<no_function>",
                                data.toString()
                            ));
                        }
                    }
                }

                if (hits.isEmpty()) {
                    bw.write("<no scalar/data hits>\n\n");
                    continue;
                }

                for (String hit : hits) {
                    bw.write(hit);
                    bw.write("\n");
                }
                bw.write("\n");
            }
        }

        println("Wrote scalar-use report to " + args[1]);
    }
}
