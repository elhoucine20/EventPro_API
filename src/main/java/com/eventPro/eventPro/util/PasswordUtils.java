package com.eventPro.eventPro.util;

import org.springframework.stereotype.Component;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

@Component
public class PasswordUtils {

    public String generateSalt(){
        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String hashPassword(String password,String salt){

        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);

            KeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes,65536,256);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();

            return Base64.getEncoder().encodeToString(hash);
        }catch (Exception e){
            throw new RuntimeException("error quand hash le mot de pass", e);
        }
    }

    public boolean verifypassword(String password,String storedHash,String salt){
        String hash = hashPassword(password,salt);
        //return hash.equals(storedHash);
        return MessageDigest.isEqual(hash.getBytes(),storedHash.getBytes());
    }

}
