package com.das.unigo.utils;

import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.nio.charset.StandardCharsets;

public class JwtUtils {

    private static final String TAG = "JwtUtils";

    /**
     * Genera el token JWT firmado para acceder a la API de Euskalmet.
     * @param privateKeyBase64 Clave privada en formato PKCS8 (Base64) sin cabeceras.
     * @param email Email del usuario de la API.
     * @return El token JWT completo o null en caso de error.
     */
    public static String generateEuskalmetToken(String privateKeyBase64, String email) {
        try {
            // 1. Generar Header
            JSONObject header = new JSONObject();
            header.put("alg", "RS256");
            header.put("typ", "JWT");
            
            // 2. Generar Payload
            long currentTimeMillis = System.currentTimeMillis();
            long iat = currentTimeMillis / 1000;
            long exp = iat + 3600; // 1 hora de validez

            JSONObject payload = new JSONObject();
            payload.put("aud", "met01.apikey");
            payload.put("iss", "UNIGO");
            payload.put("exp", exp);
            payload.put("version", "1.0.0");
            payload.put("iat", iat);
            payload.put("email", email);

            // 3. Codificar en Base64Url (sin padding)
            String encodedHeader = Base64.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            String encodedPayload = Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

            String dataToSign = encodedHeader + "." + encodedPayload;

            // 4. Firmar con RS256 (SHA256withRSA)
            // Extraer la clave privada desde Base64 (espera formato PKCS8)
            byte[] keyBytes = Base64.decode(privateKeyBase64, Base64.DEFAULT);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(spec);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();

            // 5. Codificar la firma en Base64Url
            String encodedSignature = Base64.encodeToString(signatureBytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

            // 6. Retornar el token completo
            return dataToSign + "." + encodedSignature;

        } catch (Exception e) {
            Log.e(TAG, "Error generando JWT", e);
            return null;
        }
    }
}
