package muni.fi.aes;

import static muni.fi.aes.BlockCipherMode.CBC;
import static muni.fi.aes.BlockCipherMode.CFB;
import static muni.fi.aes.BlockCipherMode.ECB;
import static muni.fi.aes.BlockCipherMode.OFB;
import muni.fi.gf2n.classes.GF2N;
import muni.fi.gf2n.classes.Matrix;
import muni.fi.gf2n.classes.MatrixGF2N;
import muni.fi.gf2n.classes.Vector;

/**
 * Class AES implements AES algorithm working with four types of Block cipher
 * modes(ECB, CBC, CFB, OFB) and with keys of three different lengths(128bit,
 * 192bit, 256bit).
 *
 * @author Jakub Lipcak, Masaryk University
 *
 */
public final class AES {

    private int[] Sbox;
    private int[] invSbox;
    private int[] Rcon;
    private BlockCipherMode mode;

    /**
     * Creates AES object and initializes S-Box, Inverse S-box and Rcon. ECB
     * Block cipher mode is set as default.
     */
    public AES() {
        //build
        Sbox = new int[256];
        invSbox = new int[256];
        Rcon = new int[256];
        mode = BlockCipherMode.ECB;

        for (int x = 0; x < 256; x++) {
            Sbox[x] = affineTransformation(x);
            invSbox[x] = inverseAffineTransformation(x);
            Rcon[x] = computeRcon(x);
        }
    }

    /**
     * Creates AES object and initializes S-Box, Inverse S-box and Rcon.
     *
     * @param mode Block cipher mode used for encryption and decryption
     */
    public AES(BlockCipherMode mode) {
        this();
        this.mode = mode;
    }

    /**
     * Returns encrypted array of bytes. Data are encrypted in blocks(16 bytes),
     * length of returned array is always divisible by 16.
     *
     * @param input array of bytes to encrypt
     * @param key key used to encrypt data
     * @param initializationVector initializationVector is used in some modes of
     * encryption
     * @return encrypted input
     */
    public byte[] encrypt(byte[] input, byte[] key, byte[] initializationVector) {

        isKeyValid(key);
        if (mode != ECB) {
            isInitializationVectorValid(initializationVector);
        }

        byte[] result = new byte[input.length];

        switch (mode) {
            case ECB: {
                return encryptECB(input, key);
            }
            case CFB: {
                return encryptCFB(input, key, initializationVector);
            }
            case CBC: {
                return encryptCBC(input, key, initializationVector);
            }
            case OFB: {
                return encryptOFB(input, key, initializationVector);
            }
        }
        return result;
    }

    /**
     * Returns decrypted array of bytes. Data are decrypted in blocks(16 bytes),
     * length of returned array is always divisible by 16.
     *
     * @param input array of bytes to decrypt
     * @param key key used to decrypt data
     * @param initializationVector initializationVector is used in some modes of
     * decryption
     * @return decrypted input
     */
    public byte[] decrypt(byte[] input, byte[] key, byte[] initializationVector) {

        isKeyValid(key);
        if (mode != ECB) {
            isInitializationVectorValid(initializationVector);
        }

        byte[] result = new byte[input.length];

        switch (mode) {
            case ECB: {
                return decryptECB(input, key);
            }
            case CFB: {
                return decryptCFB(input, key, initializationVector);
            }
            case CBC: {
                return decryptCBC(input, key, initializationVector);
            }
            case OFB: {
                return decryptOFB(input, key, initializationVector);
            }
        }
        return result;
    }

    /**
     * Returns encrypted array of bytes. Data are encrypted in blocks(16 bytes),
     * length of returned array is always divisible by 16. ECB Block Cipher Mode
     * is used here, the same blocks are encrypted to the same result.
     *
     * @param input array of bytes to encrypt
     * @param key key used to encrypt data
     * @return encrypted input
     */
    public byte[] encryptECB(byte[] input, byte[] key) {

        isKeyValid(key);

        int length = input.length % 16;

        if (length == 0) {
            length = input.length;
        } else {
            length = input.length + (16 - length);
        }

        byte[] result = new byte[length];

        for (int x = 0; x < result.length; x += 16) {
            byte[] temp = new byte[16];

            if (input.length - x < 16) {
                length = input.length - x;
            } else {
                length = 16;
            }

            System.arraycopy(input, x, temp, 0, length);
            temp = encryptBlock(temp, key);
            System.arraycopy(temp, 0, result, x, 16);
        }

        return result;
    }

    /**
     * Returns decrypted array of bytes. Data are decrypted in blocks(16 bytes),
     * length of returned array is always divisible by 16. ECB Block Cipher Mode
     * is used here, the same blocks are decrypted to the same result.
     *
     * @param input array of bytes to decrypt
     * @param key key used to decrypt data
     * @return decrypted input
     */
    public byte[] decryptECB(byte[] input, byte[] key) {

        isKeyValid(key);

        int length = input.length % 16;

        if (length == 0) {
            length = input.length;
        } else {
            length = input.length + (16 - length);
        }

        byte[] result = new byte[length];

        for (int x = 0; x < result.length; x += 16) {
            byte[] temp = new byte[16];

            if (input.length - x < 16) {
                length = input.length - x;
            } else {
                length = 16;
            }

            System.arraycopy(input, x, temp, 0, length);
            temp = decryptBlock(temp, key);
            System.arraycopy(temp, 0, result, x, 16);
        }

        return result;
    }

    /**
     * Returns encrypted block of bytes. Block consists of 16 bytes. Only first
     * 16 bytes of input are encrypted. Exception is thrown for inputs shorter
     * than 16 bytes.
     *
     * @param input array of bytes to encrypt
     * @param key key used to encrypt data
     * @return encrypted input block of data
     */
    public byte[] encryptBlock(byte[] input, byte[] key) {

        isKeyValid(key);

        if (input.length < 16) {
            throw new IllegalArgumentException("Cannot encrypt input shorter than 16 bytes(block).");
        }

        byte[] result = new byte[16];
        byte[][] expandedKey = keyExpansion(key);

        //prepare stateMatrix
        byte[][] stateMatrix = new byte[4][4];
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                stateMatrix[x][y] = input[(4 * x) + y];
            }
        }

        //InitialRound
        stateMatrix = addRoundKey(expandedKey, stateMatrix, 0);

        for (int round = 1; round <= numOfRounds(key); round++) {

            //Encription step 1: subBytes operation
            stateMatrix = subBytes(stateMatrix);

            //Encription step 2: shiftRows operation
            stateMatrix = shiftRows(stateMatrix);

            //Encription step 3: mixColumns operation
            if (round != numOfRounds(key)) {
                stateMatrix = mixColumns(stateMatrix);
            }

            //Encription step 4: addRoundKey operation
            stateMatrix = addRoundKey(expandedKey, stateMatrix, round);
        }

        //prepare result
        for (int x = 0; x < 16; x++) {
            result[x] = stateMatrix[x / 4][x % 4];
        }


        return result;
    }

    /**
     * Returns decrypted block of bytes. Block consists of 16 bytes. Only first
     * 16 bytes of input are decrypted. Exception is thrown for inputs shorter
     * than 16 bytes.
     *
     * @param input array of bytes to decrypt
     * @param key key used to decrypt data
     * @return decrypted input block of data
     */
    public byte[] decryptBlock(byte[] input, byte[] key) {

        isKeyValid(key);

        if (input.length < 16) {
            throw new IllegalArgumentException("Cannot decrypt input shorter than 16 bytes(block).");
        }

        byte[] result = new byte[16];
        byte[][] expandedKey = keyExpansion(key);

        //prepare stateMatrix
        byte[][] stateMatrix = new byte[4][4];
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                stateMatrix[x][y] = input[(4 * x) + y];
            }
        }

        //InitialRound
        stateMatrix = addRoundKey(expandedKey, stateMatrix, numOfRounds(key));

        for (int round = numOfRounds(key) - 1; round >= 0; round--) {

            //Decryption step 1: invShiftRows operation
            stateMatrix = invShiftRows(stateMatrix);

            //Decryption step 2: invSubBytes operation
            stateMatrix = invSubBytes(stateMatrix);

            //Decryption step 3: addRoundKey operation
            stateMatrix = addRoundKey(expandedKey, stateMatrix, round);

            //Decryption step 4: mixColumns operation
            if (round != 0) {
                stateMatrix = invMixColumns(stateMatrix);
            }


        }

        //prepare result
        for (int x = 0; x < 16; x++) {
            result[x] = stateMatrix[x / 4][x % 4];
        }


        return result;
    }

    //Encription step 1
    private byte[][] subBytes(byte[][] stateArray) {

        byte[][] result = new byte[4][4];

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                result[x][y] = (byte) Sbox[(stateArray[x][y] & 0xFF)];
            }
        }

        return result;
    }

    //Encription step 2
    private byte[][] shiftRows(byte[][] matrix) {
        byte[][] result = new byte[4][4];

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                result[x][y] = matrix[(x + y) % 4][y];
            }
        }

        return result;
    }

    //Encription step 3
    private byte[][] mixColumns(byte[][] stateArray) {

        byte[][] result = new byte[4][4];

        for (int x = 0; x < 4; x++) {
            byte[] temp = mixWord(stateArray[x]);
            for (int y = 0; y < 4; y++) {
                result[x][y] = temp[y];
            }
        }

        return result;
    }

    //Add round key step, the subkey is combined with the stateArray(XOR) 
    private byte[][] addRoundKey(byte[][] expandedKey, byte[][] stateArray, int round) {

        byte[][] result = new byte[4][4];

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                result[x][y] = (byte) (stateArray[x][y] ^ expandedKey[x + (4 * round)][y]);
            }
        }

        return result;
    }

    //Decryption step 1
    private byte[][] invShiftRows(byte[][] matrix) {
        byte[][] result = new byte[4][4];

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                result[x][y] = matrix[(x + (4 - y)) % 4][y];
            }
        }

        return result;
    }

    //Decryption step 2
    private byte[][] invSubBytes(byte[][] stateArray) {

        byte[][] result = new byte[4][4];

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                result[x][y] = (byte) invSbox[(stateArray[x][y] & 0xFF)];
            }
        }

        return result;
    }

    //Decryption step 4
    private byte[][] invMixColumns(byte[][] stateArray) {

        byte[][] result = new byte[4][4];

        for (int x = 0; x < 4; x++) {
            byte[] temp = invMixWord(stateArray[x]);
            for (int y = 0; y < 4; y++) {
                result[x][y] = temp[y];
            }
        }

        return result;
    }

    //Compute values necessary for mixColumns operation
    private byte[] mixWord(byte[] input) {
        byte[] result = new byte[4];

        GF2N aesField = new GF2N(283);
        Matrix mulMatrix = new Matrix(4, 4);
        MatrixGF2N aesMat = new MatrixGF2N(aesField);

        Vector mulVector = new Vector(4);
        for (int x = 0; x < 4; x++) {
            mulVector.setElement(x, input[x] & 0xFF);
        }

        /* mulMatrix:
         
         [ 2, 3, 1, 1 ]
         [ 1, 2, 3, 1 ]
         [ 1, 1, 2, 3 ]
         [ 3, 1, 1, 2 ]
         
         */
        mulMatrix.setElement(0, 0, 2);
        mulMatrix.setElement(0, 1, 3);
        mulMatrix.setElement(0, 2, 1);
        mulMatrix.setElement(0, 3, 1);
        mulMatrix.setElement(1, 0, 1);
        mulMatrix.setElement(1, 1, 2);
        mulMatrix.setElement(1, 2, 3);
        mulMatrix.setElement(1, 3, 1);
        mulMatrix.setElement(2, 0, 1);
        mulMatrix.setElement(2, 1, 1);
        mulMatrix.setElement(2, 2, 2);
        mulMatrix.setElement(2, 3, 3);
        mulMatrix.setElement(3, 0, 3);
        mulMatrix.setElement(3, 1, 1);
        mulMatrix.setElement(3, 2, 1);
        mulMatrix.setElement(3, 3, 2);

        Matrix resultMat = aesMat.multiply(mulMatrix, mulVector);
        for (int x = 0; x < 4; x++) {
            result[x] = (byte) resultMat.getElement(x, 0);
        }

        return result;
    }

    //Compute values necessary for invMixColumns operation
    private byte[] invMixWord(byte[] input) {
        byte[] result = new byte[4];

        GF2N aesField = new GF2N(283);
        Matrix mulMatrix = new Matrix(4, 4);
        MatrixGF2N aesMat = new MatrixGF2N(aesField);

        Vector mulVector = new Vector(4);
        for (int x = 0; x < 4; x++) {
            mulVector.setElement(x, (long) (input[x] & 0xFF));
        }

        /* mulMatrix:
         
         [ 14, 11, 13,  9 ]
         [  9, 14, 11, 13 ]
         [ 13,  9, 14, 11 ]
         [ 11, 13,  9, 14 ]
         
         */
        mulMatrix.setElement(0, 0, 14);
        mulMatrix.setElement(0, 1, 11);
        mulMatrix.setElement(0, 2, 13);
        mulMatrix.setElement(0, 3, 9);
        mulMatrix.setElement(1, 0, 9);
        mulMatrix.setElement(1, 1, 14);
        mulMatrix.setElement(1, 2, 11);
        mulMatrix.setElement(1, 3, 13);
        mulMatrix.setElement(2, 0, 13);
        mulMatrix.setElement(2, 1, 9);
        mulMatrix.setElement(2, 2, 14);
        mulMatrix.setElement(2, 3, 11);
        mulMatrix.setElement(3, 0, 11);
        mulMatrix.setElement(3, 1, 13);
        mulMatrix.setElement(3, 2, 9);
        mulMatrix.setElement(3, 3, 14);

        Matrix resultMat = aesMat.multiply(mulMatrix, mulVector);
        for (int x = 0; x < 4; x++) {
            result[x] = (byte) resultMat.getElement(x, 0);
        }

        return result;
    }

    //AES (Rijndael) uses a key schedule to expand key into a number of separate round keys
    private byte[][] keyExpansion(byte[] key) {
        byte[][] result = new byte[4 + (numOfRounds(key) * 4)][4];

        for (int x = 0; x < (lengthOfKeyMatrix(key)); x++) {
            result[x][0] = key[(4 * x)];
            result[x][1] = key[(4 * x) + 1];
            result[x][2] = key[(4 * x) + 2];
            result[x][3] = key[(4 * x) + 3];
        }

        int step = 1;
        int position = (lengthOfKeyMatrix(key));
        while (position < result.length) {

            if (position % (lengthOfKeyMatrix(key)) == 0) {
                System.arraycopy(rotWord(result[position - 1]), 0, result[position], 0, 4);
                System.arraycopy(subWord(result[position]), 0, result[position], 0, 4);
                result[position][0] = (byte) (result[position][0] ^ Rcon[step]);
                step++;
            } else {
                if ((lengthOfKeyMatrix(key) == 8 && (position % 8 == 4))) {
                    System.arraycopy(subWord(result[position - 1]), 0, result[position], 0, 4);
                } else {
                    System.arraycopy(result[position - 1], 0, result[position], 0, 4);
                }
            }

            result[position][0] = (byte) (result[position - lengthOfKeyMatrix(key)][0] ^ result[position][0]);
            result[position][1] = (byte) (result[position - lengthOfKeyMatrix(key)][1] ^ result[position][1]);
            result[position][2] = (byte) (result[position - lengthOfKeyMatrix(key)][2] ^ result[position][2]);
            result[position][3] = (byte) (result[position - lengthOfKeyMatrix(key)][3] ^ result[position][3]);

            position++;
        }

        return result;

    }

    //Rotate bytes of word
    private byte[] rotWord(byte[] word) {
        byte[] result = new byte[4];

        byte temp = word[0];
        result[0] = word[1];
        result[1] = word[2];
        result[2] = word[3];
        result[3] = temp;

        return result;
    }

    //Substitute bytes from word by values from S-Box
    private byte[] subWord(byte[] word) {
        byte[] result = new byte[4];

        result[0] = (byte) Sbox[word[0] & 0xFF];
        result[1] = (byte) Sbox[word[1] & 0xFF];
        result[2] = (byte) Sbox[word[2] & 0xFF];
        result[3] = (byte) Sbox[word[3] & 0xFF];

        return result;
    }

    //S-Box computation
    private int affineTransformation(int value) {

        /*Prepare affineMatrix:
         
         [ 1, 0, 0, 0, 1, 1, 1, 1 ]
         [ 1, 1, 0, 0, 0, 1, 1, 1 ]
         [ 1, 1, 1, 0, 0, 0, 1, 1 ]
         [ 1, 1, 1, 1, 0, 0, 0, 1 ]
         [ 1, 1, 1, 1, 1, 0, 0, 0 ]
         [ 0, 1, 1, 1, 1, 1, 0, 0 ]
         [ 0, 0, 1, 1, 1, 1, 1, 0 ]
         [ 0, 0, 0, 1, 1, 1, 1, 1 ]
         
         */
        Matrix affineMatrix = new Matrix(8, 8);
        int pos = 0;
        for (int x = 0; x < 8; x++) {
            pos++;
            for (int y = 0; y < 8; y++) {
                if (!((y == (pos % 8)) || ((y == ((pos + 1) % 8))) || ((y == ((pos + 2) % 8))))) {
                    affineMatrix.setElement(x, y, 1);
                }
            }
        }

        GF2N aesField = new GF2N(283);
        MatrixGF2N aesMat = new MatrixGF2N(aesField);

        Matrix resultMatrix;
        if (value != 0) {
            resultMatrix = aesMat.multiply(affineMatrix, decimalNumberToBinaryVector(aesField.invert(value)));
        } else {
            resultMatrix = aesMat.multiply(affineMatrix, decimalNumberToBinaryVector(0));
        }

        Matrix addMatrix = new Matrix(8, 1);//{1,1,0,0,0,1,1,0};
        addMatrix.setElement(0, 0, 1);
        addMatrix.setElement(1, 0, 1);
        addMatrix.setElement(2, 0, 0);
        addMatrix.setElement(3, 0, 0);
        addMatrix.setElement(4, 0, 0);
        addMatrix.setElement(5, 0, 1);
        addMatrix.setElement(6, 0, 1);
        addMatrix.setElement(7, 0, 0);

        return (int) binaryMatrixToDecimalNumber(aesMat.add(resultMatrix, addMatrix));

    }

    //Inverse S-Box computation
    private int inverseAffineTransformation(long value) {

        /*Prepare inverseAffineMatrix:
         
         [ 0, 0, 1, 0, 0, 1, 0, 1 ]
         [ 1, 0, 0, 1, 0, 0, 1, 0 ]
         [ 0, 1, 0, 0, 1, 0, 0, 1 ]
         [ 1, 0, 1, 0, 0, 1, 0, 0 ]
         [ 0, 1, 0, 1, 0, 0, 1, 0 ]
         [ 0, 0, 1, 0, 1, 0, 0, 1 ]
         [ 1, 0, 0, 1, 0, 1, 0, 0 ]
         [ 0, 1, 0, 0, 1, 0, 1, 0 ]
         
         */
        Matrix affineMatrix = new Matrix(8, 8);
        int pos = 6;
        for (int x = 0; x < 8; x++) {
            pos++;
            affineMatrix.setElement(x, pos % 8, 1);
            affineMatrix.setElement(x, (pos + 3) % 8, 1);
            affineMatrix.setElement(x, (pos + 6) % 8, 1);
        }

        GF2N aesField = new GF2N(283);
        MatrixGF2N aesMat = new MatrixGF2N(aesField);

        Matrix resultMatrix = aesMat.multiply(affineMatrix, decimalNumberToBinaryVector(value));

        Matrix addMatrix = new Matrix(8, 1);//{1,0,1,0,0,0,0,0};
        addMatrix.setElement(0, 0, 1);
        addMatrix.setElement(1, 0, 0);
        addMatrix.setElement(2, 0, 1);
        addMatrix.setElement(3, 0, 0);
        addMatrix.setElement(4, 0, 0);
        addMatrix.setElement(5, 0, 0);
        addMatrix.setElement(6, 0, 0);
        addMatrix.setElement(7, 0, 0);

        if (binaryMatrixToDecimalNumber(aesMat.add(resultMatrix, addMatrix)) != 0) {
            return (int) aesField.invert(binaryMatrixToDecimalNumber(aesMat.add(resultMatrix, addMatrix)));
        } else {
            return 0;
        }
    }

    //encrypt array of bytes in CBC mode
    private byte[] encryptCBC(byte[] input, byte[] key, byte[] initializationVector) {

        //prepare result with good length
        int length = input.length % 16;
        if (length == 0) {
            length = input.length;
        } else {
            length = input.length + (16 - length);
        }
        byte[] result = new byte[length];

        //prepare xorBlock for XOR operations, in CBC mode initializationVector is XORed first
        byte[] xorBlock = new byte[16];
        System.arraycopy(initializationVector, 0, xorBlock, 0, 16);

        for (int x = 0; x < result.length; x += 16) {
            byte[] temp = new byte[16];

            if (input.length - x < 16) {
                length = input.length - x;
            } else {
                length = 16;
            }

            //XOR with plaintext is always performed before encryption in CBC mode
            System.arraycopy(input, x, temp, 0, length);
            temp = encryptBlock(xorBlocks(temp, xorBlock), key);

            //copy computed data to result
            System.arraycopy(temp, 0, result, x, 16);

            //prepare new xorBlock
            System.arraycopy(temp, 0, xorBlock, 0, 16);
        }

        return result;
    }

    //encrypt array of bytes in CFB mode
    private byte[] encryptCFB(byte[] input, byte[] key, byte[] initializationVector) {

        //prepare result with good length
        int length = input.length % 16;
        if (length == 0) {
            length = input.length;
        } else {
            length = input.length + (16 - length);
        }
        byte[] result = new byte[length];

        //prepare data to encrypt, initializationVector is encrypted first in CFB mode 
        byte[] toEncrypt = new byte[16];
        System.arraycopy(initializationVector, 0, toEncrypt, 0, 16);

        for (int x = 0; x < result.length; x += 16) {
            byte[] temp = new byte[16];

            if (input.length - x < 16) {
                length = input.length - x;
            } else {
                length = 16;
            }


            //XOR with plaintext is always performed after encryption in CFB mode
            System.arraycopy(input, x, temp, 0, length);
            toEncrypt = encryptBlock(toEncrypt, key);
            toEncrypt = xorBlocks(toEncrypt, temp);

            //copy computed data to result
            System.arraycopy(toEncrypt, 0, result, x, 16);

        }

        return result;
    }

    //encrypt array of bytes in OFB mode
    private byte[] encryptOFB(byte[] input, byte[] key, byte[] initializationVector) {

        //prepare result with good length
        int length = input.length % 16;
        if (length == 0) {
            length = input.length;
        } else {
            length = input.length + (16 - length);
        }
        byte[] result = new byte[length];

        //prepare data to encrypt, initializationVector is encrypted first in OFB mode 
        byte[] toEncrypt = new byte[16];
        System.arraycopy(initializationVector, 0, toEncrypt, 0, 16);

        for (int x = 0; x < result.length; x += 16) {
            byte[] temp = new byte[16];

            if (input.length - x < 16) {
                length = input.length - x;
            } else {
                length = 16;
            }

            //encryption
            System.arraycopy(input, x, temp, 0, length);
            toEncrypt = encryptBlock(toEncrypt, key);

            //copy toEncrypt XOR plaintext to result
            System.arraycopy(xorBlocks(toEncrypt, temp), 0, result, x, 16);

        }

        return result;
    }

    //decrypt array of bytes in CBC mode
    private byte[] decryptCBC(byte[] input, byte[] key, byte[] initializationVector) {
        int length = input.length % 16;

        if (length == 0) {
            length = input.length;
        } else {
            length = input.length + (16 - length);
        }

        byte[] result = new byte[length];
        byte[] xorWord = new byte[16];
        System.arraycopy(initializationVector, 0, xorWord, 0, 16);

        for (int x = 0; x < result.length; x += 16) {
            byte[] temp = new byte[16];

            if (input.length - x < 16) {
                length = input.length - x;
            } else {
                length = 16;
            }

            System.arraycopy(input, x, temp, 0, length);
            temp = decryptBlock(temp, key);
            System.arraycopy(xorBlocks(temp, xorWord), 0, result, x, 16);

            System.arraycopy(input, x, xorWord, 0, length);
        }

        return result;
    }

    //decrypt array of bytes in CFB mode
    private byte[] decryptCFB(byte[] input, byte[] key, byte[] initializationVector) {

        //prepare result with good length
        int length = input.length % 16;
        if (length == 0) {
            length = input.length;
        } else {
            length = input.length + (16 - length);
        }
        byte[] result = new byte[length];

        //prepare data to decrypt, initializationVector is decrypted first in CFB mode 
        byte[] toEncrypt = new byte[16];
        System.arraycopy(initializationVector, 0, toEncrypt, 0, 16);

        for (int x = 0; x < result.length; x += 16) {
            byte[] temp = new byte[16];

            if (input.length - x < 16) {
                length = input.length - x;
            } else {
                length = 16;
            }


            //XOR with plaintext is always performed after encryption in CFB mode
            System.arraycopy(input, x, temp, 0, length);
            toEncrypt = encryptBlock(toEncrypt, key);

            //copy computed data XORed with plainText to result
            System.arraycopy(xorBlocks(toEncrypt, temp), 0, result, x, 16);

            //plainText will be decrypted in next cycle
            System.arraycopy(temp, 0, toEncrypt, 0, 16);

        }

        return result;
    }

    //decrypt array of bytes in OFB mode
    private byte[] decryptOFB(byte[] input, byte[] key, byte[] initializationVector) {

        //prepare result with good length
        int length = input.length % 16;
        if (length == 0) {
            length = input.length;
        } else {
            length = input.length + (16 - length);
        }
        byte[] result = new byte[length];

        //prepare data to decrypt, initializationVector is decrypted first in OFB mode 
        byte[] toEncrypt = new byte[16];
        System.arraycopy(initializationVector, 0, toEncrypt, 0, 16);

        for (int x = 0; x < result.length; x += 16) {
            byte[] temp = new byte[16];

            if (input.length - x < 16) {
                length = input.length - x;
            } else {
                length = 16;
            }

            System.arraycopy(input, x, temp, 0, length);
            toEncrypt = encryptBlock(toEncrypt, key);

            //copy computed data XORed with plainText to result
            System.arraycopy(xorBlocks(toEncrypt, temp), 0, result, x, 16);
        }

        return result;
    }

    private byte[] xorBlocks(byte[] word1, byte[] word2) {
        byte[] result = new byte[16];

        for (int x = 0; x < 16; x++) {
            result[x] = (byte) (word1[x] ^ word2[x]);
        }

        return result;
    }

    private Vector decimalNumberToBinaryVector(long value) {
        Vector result = new Vector(8);

        long temp = value;
        for (int x = 0; x < 8; x++) {
            result.setElement(x, temp & 1);
            temp >>= 1;
        }

        return result;
    }

    private long binaryMatrixToDecimalNumber(Matrix matrix) {
        int result = 0;

        for (int x = 7; x >= 0; x--) {
            result ^= matrix.getElement(x, 0);
            if (x != 0) {
                result <<= 1;
            }
        }

        return result;
    }

    private int computeRcon(long value) {

        if (value == 0) {
            return 141;
        }

        GF2N aesField = new GF2N(283);
        return (int) aesField.power(2, value - 1);
    }

    private int numOfRounds(byte[] key) {

        switch (key.length) {
            case 16:
                return 10;
            case 24:
                return 12;
            case 32:
                return 14;
        }

        return 0;
    }

    private int lengthOfKeyMatrix(byte[] key) {

        switch (key.length) {
            case 16:
                return 4;
            case 24:
                return 6;
            case 32:
                return 8;
        }

        return 0;
    }

    private void isKeyValid(byte[] key) {
        if ((key.length != 16) && (key.length != 24) && (key.length != 32)) {
            throw new IllegalArgumentException("Input key must be 16, 24, or 32 bytes long.");
        }
    }

    private void isInitializationVectorValid(byte[] vector) {
        if (vector.length != 16) {
            throw new IllegalArgumentException("Input vector must be 16 bytes long.");
        }
    }
}
