import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SearchPatternsFromMap extends GhidraScript {
    private static class ParsedPattern {
        byte[] bytes;
        byte[] masks;
    }

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 2) {
            println("Usage: SearchPatternsFromMap <pattern_map.tsv> <out_path>");
            return;
        }

        List<String> lines = Files.readAllLines(Paths.get(args[0]));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(args[1]))) {
            bw.write("label\thit_count\thits\n");

            for (String line : lines) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }

                String[] parts = s.split("\t", 2);
                if (parts.length < 2) {
                    continue;
                }

                String label = parts[0].trim();
                ParsedPattern pattern = parsePattern(parts[1].trim());
                List<String> hits = findHits(pattern.bytes, pattern.masks, 8);

                bw.write(label);
                bw.write("\t");
                bw.write(Integer.toString(hits.size()));
                bw.write("\t");
                bw.write(String.join(";", hits));
                bw.write("\n");
            }
        }
    }

    private List<String> findHits(byte[] bytes, byte[] masks, int limit) throws Exception {
        List<String> hits = new ArrayList<>();
        Memory memory = currentProgram.getMemory();

        for (MemoryBlock block : memory.getBlocks()) {
            if (!block.isExecute()) {
                continue;
            }

            Address start = block.getStart();
            Address end = block.getEnd();

            while (start != null && start.compareTo(end) <= 0 && !monitor.isCancelled()) {
                Address found = memory.findBytes(start, end, bytes, masks, true, monitor);
                if (found == null) {
                    break;
                }

                hits.add(found.toString());
                if (hits.size() >= limit) {
                    return hits;
                }

                start = found.next();
            }
        }

        return hits;
    }

    private ParsedPattern parsePattern(String pattern) {
        String[] tokens = pattern.split("\\s+");
        byte[] bytes = new byte[tokens.length];
        byte[] masks = new byte[tokens.length];

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.equals("?") || token.equals("??")) {
                bytes[i] = 0;
                masks[i] = 0;
            }
            else {
                bytes[i] = (byte) Integer.parseInt(token, 16);
                masks[i] = (byte) 0xff;
            }
        }

        ParsedPattern parsed = new ParsedPattern();
        parsed.bytes = bytes;
        parsed.masks = masks;
        return parsed;
    }
}
