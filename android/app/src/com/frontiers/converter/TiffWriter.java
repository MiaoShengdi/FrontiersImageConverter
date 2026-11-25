package com.frontiers.converter;

import java.io.ByteArrayOutputStream;

/**
 * 小端（II）单条带 LZW TIFF 写入器。
 * 固定标签：RGB（Photometric=2）、每像素 3 样本、8 位、300 dpi（ResolutionUnit=inch）。
 */
class TiffWriter {

    private static final int TYPE_SHORT = 3;
    private static final int TYPE_LONG = 4;
    private static final int TYPE_RATIONAL = 5;

    static byte[] write(int width, int height, byte[] rgb) {
        byte[] strip = LzwEncoder.encode(rgb);

        int ifdOffset = 8;
        int entryCount = 12;
        int ifdSize = 2 + entryCount * 12 + 4;
        int auxOffset = ifdOffset + ifdSize;          // 158
        int bpsOffset = auxOffset;                     // 6 bytes
        int xresOffset = bpsOffset + 6;                // 8 bytes
        int yresOffset = xresOffset + 8;               // 8 bytes
        int stripOffset = yresOffset + 8;              // 180

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        // header
        writeAscii(o, "II");
        writeU16(o, 42);
        writeU32(o, ifdOffset);

        // IFD
        writeU16(o, entryCount);
        entry(o, 256, TYPE_LONG, 1, width);
        entry(o, 257, TYPE_LONG, 1, height);
        entry(o, 258, TYPE_SHORT, 3, bpsOffset);
        entry(o, 259, TYPE_SHORT, 1, 5);
        entry(o, 262, TYPE_SHORT, 1, 2);
        entry(o, 273, TYPE_LONG, 1, stripOffset);
        entry(o, 277, TYPE_SHORT, 1, 3);
        entry(o, 278, TYPE_LONG, 1, height);
        entry(o, 279, TYPE_LONG, 1, strip.length);
        entry(o, 282, TYPE_RATIONAL, 1, xresOffset);
        entry(o, 283, TYPE_RATIONAL, 1, yresOffset);
        entry(o, 296, TYPE_SHORT, 1, 2);
        writeU32(o, 0);

        // aux data
        writeU16(o, 8);
        writeU16(o, 8);
        writeU16(o, 8);
        writeU32(o, 300);
        writeU32(o, 1);
        writeU32(o, 300);
        writeU32(o, 1);

        o.write(strip, 0, strip.length);
        return o.toByteArray();
    }

    private static void entry(ByteArrayOutputStream o, int tag, int type,
                              int count, int value) {
        writeU16(o, tag);
        writeU16(o, type);
        writeU32(o, count);
        if (type == TYPE_SHORT) {
            writeU16(o, value);
            writeU16(o, 0);
        } else {
            writeU32(o, value);
        }
    }

    private static void writeAscii(ByteArrayOutputStream o, String s) {
        byte[] b = s.getBytes();
        o.write(b, 0, b.length);
    }

    private static void writeU16(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff);
        o.write((v >>> 8) & 0xff);
    }

    private static void writeU32(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff);
        o.write((v >>> 8) & 0xff);
        o.write((v >>> 16) & 0xff);
        o.write((v >>> 24) & 0xff);
    }
}
