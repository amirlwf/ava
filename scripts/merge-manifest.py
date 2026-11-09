#!/usr/bin/env python3
"""Build the link-time AndroidManifest.xml for the manual (no-Gradle) build.

Two things AGP normally does are reproduced here:
1. Inject the `package` attribute (AGP takes it from the namespace).
2. Merge the library-manifest components that the dependency closure
   declares. Verified by reading the AAR manifests in
   .build-tools/aar-manifests/ (all 17 checked AARs; only these 3 declare
   components — see ENV.md):
     - androidx.startup:startup-runtime 1.1.1
         -> InitializationProvider (authorities ${applicationId}.androidx-startup)
     - androidx.lifecycle:lifecycle-process 2.4.1
         -> meta-data ProcessLifecycleInitializer = androidx.startup
     - androidx.profileinstaller:profileinstaller 1.4.0
         -> meta-data ProfileInstallerInitializer = androidx.startup
         -> ProfileInstallReceiver (+ 4 intent-filters)
   The source AndroidManifest.xml is left untouched (Gradle-clean).

Usage: merge-manifest.py <src-manifest> <out-manifest> <package>
"""
import sys

SRC, OUT, PACKAGE = sys.argv[1], sys.argv[2], sys.argv[3]

MERGED = f"""
        <!-- Merged from library AAR manifests (see merge-manifest.py). -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="{PACKAGE}.androidx-startup"
            android:exported="false">
            <meta-data
                android:name="androidx.lifecycle.ProcessLifecycleInitializer"
                android:value="androidx.startup" />
            <meta-data
                android:name="androidx.profileinstaller.ProfileInstallerInitializer"
                android:value="androidx.startup" />
        </provider>

        <receiver
            android:name="androidx.profileinstaller.ProfileInstallReceiver"
            android:directBootAware="false"
            android:enabled="true"
            android:exported="true"
            android:permission="android.permission.DUMP">
            <intent-filter>
                <action android:name="androidx.profileinstaller.action.INSTALL_PROFILE" />
            </intent-filter>
            <intent-filter>
                <action android:name="androidx.profileinstaller.action.SKIP_FILE" />
            </intent-filter>
            <intent-filter>
                <action android:name="androidx.profileinstaller.action.SAVE_PROFILE" />
            </intent-filter>
            <intent-filter>
                <action android:name="androidx.profileinstaller.action.BENCHMARK_OPERATION" />
            </intent-filter>
        </receiver>
"""

text = open(SRC).read()
assert "<manifest " in text and "</application>" in text
text = text.replace("<manifest ", f'<manifest package="{PACKAGE}" ', 1)
text = text.replace("    </application>", MERGED + "    </application>", 1)
open(OUT, "w").write(text)
print(f"merged manifest written: {OUT}")
