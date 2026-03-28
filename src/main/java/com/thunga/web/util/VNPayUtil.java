package com.thunga.web.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * VNPay Utility Class
 * Xử lý tạo request, verify signature và các hàm liên quan đến VNPay
 */
public class VNPayUtil {

    /**
     * Mã hóa SHA256
     */
    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException();
            }
            final javax.crypto.Mac hmac512 = javax.crypto.Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes();
            final javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(hmacKeyBytes, 0, hmacKeyBytes.length, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * Encode URL
     */
    public static String urlEncode(String data) throws UnsupportedEncodingException {
        if (data != null) {
            return URLEncoder.encode(data, "UTF-8");
        }
        return "";
    }

    /**
     * URL Decode
     */
    public static String urlDecode(String data) throws UnsupportedEncodingException {
        if (data != null) {
            return java.net.URLDecoder.decode(data, "UTF-8");
        }
        return "";
    }

    /**
     * Sort parameter map
     */
    public static String getParameterData(Map fields) throws UnsupportedEncodingException {
        List fieldNames = new ArrayList(fields.keySet());
        Collections.sort(fieldNames);

        StringBuilder data = new StringBuilder();
        Iterator itr = fieldNames.iterator();
        boolean first = true;

        while (itr.hasNext()) {
            String fieldName = (String) itr.next();
            String fieldValue = (String) fields.get(fieldName);

            if (fieldValue != null && fieldValue.length() > 0) {
                if (!first) data.append("&");
                data.append(URLEncoder.encode(fieldName, StandardCharsets.UTF_8.toString()));
                data.append("=");
                data.append(URLEncoder.encode(fieldValue, StandardCharsets.UTF_8.toString()));
                first = false;
            }
        }
        return data.toString();
    }

    /**
     * Lấy ngày giờ định dạng theo VNPay
     */
    public static String getTimeStamp() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    /**
     * Tạo mã giao dịch ngẫu nhiên
     */
    public static String getRandomNumber(int len) {
        Random rnd = new Random();
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Verify checksum from VNPay callback
     */
    public static boolean verifySignature(String inputHash, String dataToSign, String secretKey) {
        String calculatedHash = VNPayUtil.hmacSHA512(secretKey, dataToSign);
        return calculatedHash.equals(inputHash);
    }
}