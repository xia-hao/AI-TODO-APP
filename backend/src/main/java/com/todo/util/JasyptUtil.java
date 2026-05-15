package com.todo.util;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JasyptUtil {

    private static String password;

    public JasyptUtil(@Value("${jasypt.encryptor.password}") String password) {
        JasyptUtil.password = password;
    }

    private static StandardPBEStringEncryptor createEncryptor() {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setPassword(password);
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setSaltGenerator(new RandomSaltGenerator());
        encryptor.setKeyObtentionIterations(1000);
        return encryptor;
    }

    public static String encrypt(String plainText) {
        return "ENC(" + createEncryptor().encrypt(plainText) + ")";
    }

    public static String decrypt(String encryptedValue) {
        String raw = encryptedValue;
        if (raw.startsWith("ENC(") && raw.endsWith(")")) {
            raw = raw.substring(4, raw.length() - 1);
        }
        return createEncryptor().decrypt(raw);
    }

    public static void main(String[] args) {
        password = "dev-master-key";
        String value = "需要加密的内容";
        try {
            String enc = encrypt(value);
            System.out.println("加密: " + enc);
            System.out.println("解密: " + decrypt(enc));
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
            System.exit(1);
        }
    }
}
