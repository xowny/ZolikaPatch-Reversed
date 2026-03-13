import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.scalar.Scalar;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FindDisplacementUses extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            println("Usage: FindDisplacementUses <hex_values...|comma_hex_values> <out_path>");
            return;
        }

        List<Long> targets = new ArrayList<>();
        for (int i = 0; i < args.length - 1; i++) {
            for (String part : args[i].split(",")) {
                String s = part.trim();
                if (s.isEmpty()) {
                    continue;
                }
                targets.add(Long.parseUnsignedLong(s.replace("0x", ""), 16) & 0xffffffffL);
            }
        }

        String outPath = args[args.length - 1];
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outPath))) {
            bw.write("Targets: " + Arrays.toString(targets.toArray()) + "\n\n");
            Instruction ins = getFirstInstruction();
            while (ins != null && !monitor.isCancelled()) {
                boolean matched = false;
                for (int op = 0; op < ins.getNumOperands() && !matched; op++) {
                    Object[] objs = ins.getOpObjects(op);
                    for (Object obj : objs) {
                        if (obj instanceof Scalar) {
                            long value = (((Scalar) obj).getUnsignedValue()) & 0xffffffffL;
                            if (targets.contains(value)) {
                                Function fn = getFunctionContaining(ins.getAddress());
                                bw.write(String.format(
                                    "%s\t%s\t%s\n",
                                    ins.getAddress(),
                                    fn != null ? fn.getName() : "<no_function>",
                                    ins.toString()
                                ));
                                matched = true;
                                break;
                            }
                        }
                    }
                }
                ins = ins.getNext();
            }
        }

        println("Wrote displacement-use report to " + outPath);
    }
}
