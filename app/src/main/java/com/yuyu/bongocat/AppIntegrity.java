package com.yuyu.bongocat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class AppIntegrity {
    private static final String PACKAGE_NAME = "com.yuyu.bongocat";

    private AppIntegrity() {}

    static void requireTrusted(Context context) {
        if (!isTrusted(context)) throw new SecurityException("安装包来源验证失败，请从项目发布页重新下载。");
    }

    @SuppressWarnings("deprecation")
    static boolean isTrusted(Context context) {
        if (!PACKAGE_NAME.equals(context.getPackageName())) return false;
        try {
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE_NAME, flags);
            if (!PACKAGE_NAME.equals(info.packageName)) return false;
            Signature[] signers;
            if (Build.VERSION.SDK_INT >= 28) {
                if (info.signingInfo == null) return false;
                // 只接受当前签名者，历史证书中出现原签名不能单独放行。
                signers = info.signingInfo.getApkContentsSigners();
            } else signers = info.signatures;
            if (signers == null || signers.length != 1 || signers[0] == null) return false;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray());
            StringBuilder fingerprint = new StringBuilder(64);
            for (byte value : digest) fingerprint.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return BuildConfig.SIGNING_CERT_SHA256.contentEquals(fingerprint);
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException | RuntimeException error) {
            return false;
        }
    }
}
