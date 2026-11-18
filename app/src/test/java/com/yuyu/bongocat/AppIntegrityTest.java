package com.yuyu.bongocat;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 28, 34}, application = Application.class)
public class AppIntegrityTest {
    private Application app;
    private Signature official;
    private final Signature other = new Signature(new byte[] {3, 1, 4, 1, 5});

    @Before public void setup() throws Exception {
        app = RuntimeEnvironment.getApplication();
        File certificate = new File(System.getProperty("bongocat.projectDir"), "security/bongocat-signing-cert.pem");
        try (FileInputStream input = new FileInputStream(certificate)) {
            official = new Signature(CertificateFactory.getInstance("X.509").generateCertificate(input).getEncoded());
        }
    }

    @SuppressWarnings("deprecation")
    private PackageInfo install(Signature... signatures) {
        PackageInfo info = new PackageInfo();
        info.packageName = app.getPackageName();
        info.applicationInfo = app.getApplicationInfo();
        info.signatures = signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo = new SigningInfo();
            shadowOf(info.signingInfo).setSignatures(signatures);
        }
        shadowOf(app.getPackageManager()).installPackage(info);
        return info;
    }

    @Test public void originalSigningIdentityIsAccepted() {
        install(official);
        assertTrue(AppIntegrity.isTrusted(app));
        AppIntegrity.requireTrusted(app);
    }

    @Test public void foreignAndMissingSignaturesAreRejected() {
        install(other);
        assertFalse(AppIntegrity.isTrusted(app));
        assertThrows(SecurityException.class, () -> AppIntegrity.requireTrusted(app));
        install();
        assertFalse(AppIntegrity.isTrusted(app));
        install((Signature[]) null);
        assertFalse(AppIntegrity.isTrusted(app));
    }

    @Test public void additionalSignerIsRejected() {
        install(official, other);
        assertFalse(AppIntegrity.isTrusted(app));
    }

    @Test public void oldOfficialCertificateDoesNotAuthorizeForeignCurrentSigner() {
        PackageInfo info = install(other);
        if (Build.VERSION.SDK_INT >= 28) {
            shadowOf(info.signingInfo).setPastSigningCertificates(new Signature[] {official, other});
            shadowOf(app.getPackageManager()).installPackage(info);
        }
        assertFalse(AppIntegrity.isTrusted(app));
    }

    @Test public void renamedPackageIsRejected() {
        install(official);
        Context renamed = new ContextWrapper(app) {
            @Override public String getPackageName() { return "com.example.repacked"; }
        };
        assertFalse(AppIntegrity.isTrusted(renamed));
    }

    @Test public void unavailablePackageInformationIsRejected() {
        shadowOf(app.getPackageManager()).removePackage(app.getPackageName());
        assertFalse(AppIntegrity.isTrusted(app));
    }

    @Test public void releaseStartupRejectsForeignSignerBeforeInitializingRive() {
        install(other);
        if (BuildConfig.DEBUG) {
            assertThrows(SecurityException.class, () -> AppIntegrity.requireTrusted(app));
            return;
        }
        PetApplication candidate = new PetApplication();
        ReflectionHelpers.callInstanceMethod(candidate, "attach", ReflectionHelpers.ClassParameter.from(Context.class, app));
        assertThrows(SecurityException.class, candidate::onCreate);
    }
}
