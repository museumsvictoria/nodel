package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.util.UUID;

public class UUIDs {

    /**
     * Takes a normal UUID string or URL friendly/compressed string.
     */
    public static UUID fromString(String str) {
        UUID result;

        if (str.indexOf('-') >= 0) {
            // normal UUID format
            result = UUID.fromString(str);

        } else {
            // compressed UUID format

            // pad if necessary
            int delta = (3 - str.length() % 3);

            String rawbase64;
            if (delta == 1)
                rawbase64 = str + "=";
            else if (delta == 2)
                rawbase64 = str + "==";
            else
                rawbase64 = str;

            // replace URL friendly codes
            rawbase64.replace('-', '+');
            rawbase64.replace('_', '/');

            byte[] binaryUUID = Base64.decode(rawbase64);
            if (binaryUUID.length != 16)
                throw new IllegalArgumentException("UUID is not exactly 16 bytes");

            result = uuidFromBytes(binaryUUID);
        }

        return result;
    } // (method)

    /**
     * Returns a compressed and URL friendly string for a UUID.
     */
    public static String toCompressedString(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();

        byte[] data = new byte[16];

        for (int a = 7; a >= 0; a--) {
            data[a] = (byte) (msb & 0xff);
            msb >>>= 8;
        } // (for)

        for (int a = 15; a >= 8; a--) {
            data[a] = (byte) (lsb & 0xff);
            lsb >>>= 8;
        } // (for)

        String rawbase64 = Base64.encode(data);

        // drop the padding ('==')
        // and use URL friendly characters
        rawbase64 = rawbase64.substring(0, rawbase64.length() - 2);
        rawbase64.replace('+', '-');
        rawbase64.replace('/', '_');

        return rawbase64;
    } // (method)

    private static UUID uuidFromBytes(byte[] data) {
        long msb = 0;
        long lsb = 0;

        for (int i = 0; i < 8; i++)
            msb = (msb << 8) | (data[i] & 0xff);
        for (int i = 8; i < 16; i++)
            lsb = (lsb << 8) | (data[i] & 0xff);

        UUID result = new UUID(msb, lsb);
        return result;
    } // (method)

} // (class)
