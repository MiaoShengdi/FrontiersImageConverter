package com.frontiers.converter;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 纯 Java LZW 压缩器（TIFF 变体）：
 * 9–12 位变长码，码流按高位优先（MSB）打包。
 * 位宽按字典增长切换：待分配码达到 511/1023/2047 后，下一个输出码用 10/11/12 位。
 * 表满（待分配码为 4095）时按当前位宽发 Clear 并重置。
 */
class LzwEncoder {

    private static final int CLEAR = 256;
    private static final int EOI = 257;

    static byte[] encode(byte[] input) {
        HashMap<Integer, Integer> table = new HashMap<Integer, Integer>(5000);
        int nextCode = 258;
        int codeSize = 9;

        BitWriter bw = BitWriter.create();
        bw.write(CLEAR, codeSize);

        int w = input[0] & 0xff;
        for (int i = 1; i < input.length; i++) {
            int k = input[i] & 0xff;
            int key = (w << 8) | k;
            Integer found = table.get(key);
            if (found != null) {
                w = found;
            } else {
                bw.write(w, codeSize);
                if (nextCode == 4095) {
                    bw.write(CLEAR, codeSize);
                    table = new HashMap<Integer, Integer>(5000);
                    nextCode = 258;
                    codeSize = 9;
                } else {
                    table.put(key, nextCode++);
                    // 编码器比解码器多一条已添加记录，阈值取 512/1024/2048
                    if (nextCode == 512 || nextCode == 1024 || nextCode == 2048) {
                        codeSize++;
                    }
                }
                w = k;
            }
        }
        bw.write(w, codeSize);
        bw.write(EOI, codeSize);
        return bw.toByteArray();
    }

    // 调试用：返回未打包的码序列
    static List<Integer> encodeCodes(byte[] input) {
        HashMap<Integer, Integer> table = new HashMap<Integer, Integer>(5000);
        int nextCode = 258;
        List<Integer> codes = new ArrayList<Integer>();
        codes.add(CLEAR);
        int w = input[0] & 0xff;
        for (int i = 1; i < input.length; i++) {
            int k = input[i] & 0xff;
            int key = (w << 8) | k;
            Integer found = table.get(key);
            if (found != null) {
                w = found;
            } else {
                codes.add(w);
                if (nextCode == 4095) {
                    codes.add(CLEAR);
                    table = new HashMap<Integer, Integer>(5000);
                    nextCode = 258;
                } else {
                    table.put(key, nextCode++);
                }
                w = k;
            }
        }
        codes.add(w);
        codes.add(EOI);
        return codes;
    }

    // 高位优先（MSB）打包器
    private static class BitWriter {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int current = 0;
        int bits = 0;

        static BitWriter create() {
            return new BitWriter();
        }

        void write(int code, int len) {
            for (int i = len - 1; i >= 0; i--) {
                int bit = (code >> i) & 1;
                current = (current << 1) | bit;
                bits++;
                if (bits == 8) {
                    out.write(current & 0xff);
                    current = 0;
                    bits = 0;
                }
            }
        }

        byte[] toByteArray() {
            if (bits > 0) {
                // 剩余位补 0，凑满一个字节
                out.write((current << (8 - bits)) & 0xff);
            }
            return out.toByteArray();
        }
    }
}
