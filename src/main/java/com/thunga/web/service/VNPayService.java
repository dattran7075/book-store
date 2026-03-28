package com.thunga.web.service;

import com.thunga.web.config.VNPayConfig;
import com.thunga.web.entity.Order;
import com.thunga.web.util.VNPayUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * VNPay Payment Service
 * Handles payment operations with VNPay gateway
 */
@Service
public class VNPayService {

    @Autowired
    private VNPayConfig vnpayConfig;

    @Autowired
    private OrderService orderService;

    /**
     * Create VNPay payment link
     *
     * @param order   Order object containing payment details
     * @param baseUrl Application base URL (e.g., http://localhost:8080)
     * @return Payment link URL to redirect user to VNPay gateway
     * @throws UnsupportedEncodingException if encoding fails
     */
    public String createPaymentLink(Order order, String baseUrl, HttpServletRequest request)
            throws UnsupportedEncodingException {

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        double actualAmount = order.getFinalTotal();

        vnp_Params.put("vnp_Amount", String.valueOf((long) (actualAmount * 100)));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", order.getId() + "" + VNPayUtil.getRandomNumber(6));
        vnp_Params.put("vnp_OrderInfo", "Payment for order #" + order.getId());
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", baseUrl + "/vnpay-return");
        vnp_Params.put("vnp_CreateDate", VNPayUtil.getTimeStamp());

        // IP Address
        String ip = request.getHeader("X-FORWARDED-FOR");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(ip)) ip = "127.0.0.1";
        vnp_Params.put("vnp_IpAddr", ip);

        // BƯỚC 1: Sắp xếp key theo alphabet và tính chữ ký
        // getParameterData trả về string đã URL-encoded: key=value&key2=value2
        String dataToSign = VNPayUtil.getParameterData(vnp_Params);

        // BƯỚC 2: Tính HMAC trên chuỗi đã encode
        String vnpSecureHash = VNPayUtil.hmacSHA512(vnpayConfig.getHashSecret(), dataToSign);

        // BƯỚC 3: Build URL = paymentUrl + ? + dataToSign + &vnp_SecureHash=hash
        // KHÔNG encode lại hash, KHÔNG put hash vào map rồi getParameterData lần nữa
        String paymentUrl = vnpayConfig.getPaymentUrl()
                + "?" + dataToSign
                + "&vnp_SecureHash=" + vnpSecureHash;

        return paymentUrl;
    }

    // Helper lấy IP
    private String getIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }
        // Nếu là IPv6 localhost, đổi thành IPv4
        if ("0:0:0:0:0:0:0:1".equals(ipAddress)) {
            ipAddress = "127.0.0.1";
        }
        return ipAddress;
    }

    /**
     * Verify VNPay callback response signature
     * This ensures the callback is genuinely from VNPay and not tampered
     *
     * @param requestParams Map containing all callback parameters from VNPay
     * @return true if signature is valid, false otherwise
     * @throws UnsupportedEncodingException if encoding fails
     */
    public boolean verifyPaymentResponse(Map<String, String> requestParams) throws UnsupportedEncodingException {
        String vnp_SecureHash = requestParams.get("vnp_SecureHash");
        if (vnp_SecureHash == null) {
            return false;
        }

        // Remove secure hash and hash type from parameters for verification
        requestParams.remove("vnp_SecureHash");
        requestParams.remove("vnp_SecureHashType");

        // Sort and format remaining parameters
        String dataToSign = VNPayUtil.getParameterData(requestParams);
        String calculatedHash = VNPayUtil.hmacSHA512(vnpayConfig.getHashSecret(), dataToSign);

        // Compare with received hash
        return calculatedHash.equals(vnp_SecureHash);
    }

    /**
     * Get payment result message based on VNPay response code
     *
     * @param responseCode Response code from VNPay callback
     * @return Human-readable message describing the payment result
     */
    public String getPaymentStatusMessage(String responseCode) {
        switch (responseCode) {
            case "00":
                return "Transaction successful";
            case "07":
                return "Amount deducted successfully but transaction is suspicious (possible fraud)";
            case "09":
                return "Transaction failed: Customer cancelled payment";
            case "10":
                return "Transaction failed: Identification verification failed 3 times";
            case "11":
                return "Transaction failed: Payment timeout exceeded";
            case "12":
                return "Transaction failed: Customer card or account is locked";
            case "13":
                return "Transaction failed: Wrong OTP entered 3 times";
            case "14":
                return "Transaction failed: 3D Secure authentication failed 3 times";
            case "15":
                return "Transaction failed: Customer cancelled the transaction";
            case "21":
                return "Transaction failed: Invalid amount";
            case "24":
                return "Transaction failed: Customer cancelled the transaction";
            case "25":
                return "Transaction failed: Bank rejected the transaction";
            case "33":
                return "Transaction failed: Invalid validation information";
            case "34":
                return "Transaction failed: Transaction limit exceeded";
            case "40":
                return "Transaction failed: Invalid transaction information";
            case "41":
                return "Transaction failed: Invalid information format";
            case "42":
                return "Transaction failed: Merchant account is locked";
            case "43":
                return "Transaction failed: Invalid amount format";
            case "44":
                return "Transaction failed: Invalid IP address";
            case "45":
                return "Transaction failed: Invalid transaction ID";
            case "46":
                return "Transaction failed: Merchant IP is not registered";
            case "47":
                return "Transaction failed: Merchant account is blocked";
            case "48":
                return "Transaction failed: Transaction type not supported";
            case "49":
                return "Transaction failed: Unknown error";
            case "50":
                return "Transaction failed: Refund not supported for this merchant";
            case "51":
                return "Transaction failed: Merchant has no bank account for refund";
            case "65":
                return "Transaction failed: Merchant account suspended due to overdue payment";
            case "75":
                return "Transaction failed: Bank rejected (exceeding transaction limit)";
            case "79":
                return "Transaction failed: Wrong payment password entered too many times";
            case "99":
                return "Transaction failed: Other errors";
            default:
                return "Transaction failed: Unknown error code " + responseCode;
        }
    }

    /**
     * Parse Order ID from VNPay transaction reference
     * Transaction reference format: orderId + 6 random digits
     * Example: orderId=123, txnRef=123456789 -> extract 123
     *
     * @param txnRef Transaction reference from VNPay
     * @return Parsed order ID or null if parsing fails
     */
    public Integer parseOrderId(String txnRef) {
        if (txnRef == null || txnRef.length() < 7) {
            return null;
        }
        try {
            // Remove last 6 random digits to get order ID
            String orderIdStr = txnRef.substring(0, txnRef.length() - 6);
            return Integer.parseInt(orderIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get VNPay response code description
     *
     * @param responseCode Response code
     * @return Description of the response code
     */
    public String getResponseCodeDescription(String responseCode) {
        if ("00".equals(responseCode)) {
            return "Success";
        } else {
            return "Failed - " + getPaymentStatusMessage(responseCode);
        }
    }
}