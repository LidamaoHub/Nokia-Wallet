PYTHON ?= python3
DEPS ?= .deps/j2me
export DEPS := $(abspath $(DEPS))
JAVA ?= java
JAVAC ?= javac
EVIDENCE_DIR ?= build/evidence
TEST_JAR ?= dist/nwallet-0.3.1/NWallet.jar
CP := $(DEPS)/cldcapi11-2.0.4.jar:$(DEPS)/midpapi20-2.0.4.jar:build/stubs
.PHONY: all compile nwallet test test-radio test-consent test-wallet-flow test-ui
all: nwallet
# Compile-only gate deliberately leaves every existing installable JAR untouched.
compile:
	$(PYTHON) tools/build.py prepare
	$(JAVA) -jar $(DEPS)/ecj-3.33.0.jar -source 1.3 -target 1.2 -encoding UTF-8 -bootclasspath $(CP) -nowarn -d build/stubs @build/stubs.args
	$(JAVA) -jar $(DEPS)/ecj-3.33.0.jar -source 1.3 -target 1.2 -encoding UTF-8 -bootclasspath $(CP) -nowarn -d build/classes @build/sources.args
nwallet: compile
	$(JAVA) -jar $(DEPS)/proguard-7.9.1/lib/proguard.jar @wallet.pro -injars build/classes -outjars build/preverified -libraryjars $(DEPS)/cldcapi11-2.0.4.jar -libraryjars $(DEPS)/midpapi20-2.0.4.jar -libraryjars build/stubs
	$(PYTHON) tools/build.py pack-nwallet
	$(PYTHON) tools/build.py check-nwallet
test:
	mkdir -p build/tests
	$(JAVAC) -encoding UTF-8 -cp $(TEST_JAR) -d build/tests tests/WalletChecks.java tests/TransportChecks.java tests/ProgressChecks.java tests/InputAddressChecks.java
	$(JAVA) -cp $(TEST_JAR):build/tests WalletChecks
	$(JAVA) -cp $(TEST_JAR):build/tests ProgressChecks
	$(JAVA) -cp $(TEST_JAR):build/tests InputAddressChecks
test-radio:
	mkdir -p build/radio-tests
	$(JAVAC) -encoding UTF-8 -cp $(TEST_JAR):$(CP) -d build/radio-tests tests/ui/javax/microedition/lcdui/*.java tests/ui/javax/microedition/midlet/*.java tests/RadioDiagnosticsChecks.java
	$(JAVA) -Djava.awt.headless=true -cp build/radio-tests:$(TEST_JAR):$(CP) RadioDiagnosticsChecks

test-consent:
	mkdir -p build/consent-tests $(EVIDENCE_DIR)
	$(JAVAC) -encoding UTF-8 -cp $(TEST_JAR):$(CP) -d build/consent-tests tests/ui/javax/microedition/lcdui/*.java tests/ui/javax/microedition/midlet/*.java tests/ui/javax/microedition/rms/*.java tests/radio/javax/microedition/io/Connector.java tests/MIDletConsentChecks.java
	$(JAVA) -cp build/consent-tests:$(TEST_JAR):$(CP) MIDletConsentChecks $(EVIDENCE_DIR)/midlet-frames.txt

test-wallet-flow:
	mkdir -p build/flow-tests $(EVIDENCE_DIR)
	$(JAVAC) -encoding UTF-8 -cp $(TEST_JAR):$(CP) -d build/flow-tests tests/ui/javax/microedition/lcdui/*.java tests/ui/javax/microedition/midlet/*.java tests/ui/javax/microedition/rms/*.java tests/radio/javax/microedition/io/Connector.java tests/MIDletConsentChecks.java tests/WalletFlowChecks.java
	$(JAVA) -cp build/flow-tests:$(TEST_JAR):$(CP) WalletFlowChecks $(EVIDENCE_DIR)/wallet-frames.txt

# Run after compile/nwallet. Canvas navigation checks use source classes;
# production screenshots use TEST_JAR, never a browser prototype.
test-ui:
	mkdir -p build/ui-tests $(EVIDENCE_DIR)
	$(JAVAC) -encoding UTF-8 -cp build/classes:$(CP) -d build/ui-tests tests/ui/javax/microedition/lcdui/*.java tests/ui/javax/microedition/midlet/*.java tests/ui/javax/microedition/rms/*.java tests/CanvasChecks.java tests/NativeUISnapshots.java
	$(JAVA) -Djava.awt.headless=true -Dnwallet.test.output=$(EVIDENCE_DIR) -cp build/ui-tests:build/classes:$(CP) CanvasChecks
	$(JAVA) -Djava.awt.headless=true -cp build/ui-tests:$(TEST_JAR):$(CP) NativeUISnapshots $(EVIDENCE_DIR)/screens
	$(JAVA) -Djava.awt.headless=true -Dnwallet.test.small=15 -Dnwallet.test.medium=20 -cp build/ui-tests:$(TEST_JAR):$(CP) NativeUISnapshots $(EVIDENCE_DIR)/screens-large-font
