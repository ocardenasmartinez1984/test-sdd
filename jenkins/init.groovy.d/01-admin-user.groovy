// Provisions a basic admin user and security realm when the setup wizard is
// disabled (jenkins.install.runSetupWizard=false). Credentials come from the
// JENKINS_ADMIN_ID / JENKINS_ADMIN_PASSWORD environment variables.
//
// This runs on every boot but is idempotent: it only creates the user/realm if
// not already configured.
import jenkins.model.Jenkins
import hudson.security.HudsonPrivateSecurityRealm
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy

def instance = Jenkins.get()
def env = System.getenv()

def adminId = env['JENKINS_ADMIN_ID'] ?: 'admin'
def adminPw = env['JENKINS_ADMIN_PASSWORD'] ?: 'admin123'

if (!(instance.getSecurityRealm() instanceof HudsonPrivateSecurityRealm)) {
    def realm = new HudsonPrivateSecurityRealm(false)
    realm.createAccount(adminId, adminPw)
    instance.setSecurityRealm(realm)

    def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
    strategy.setAllowAnonymousRead(false)
    instance.setAuthorizationStrategy(strategy)

    instance.save()
    println "--> init.groovy.d: created admin user '${adminId}' and security realm"
} else {
    println "--> init.groovy.d: security realm already configured, skipping"
}
