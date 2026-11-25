import { GoogleLogin } from '@react-oauth/google';
import { useState } from 'react';
import { errorMessage } from '@/api/client';
import type { User } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { useTheme } from '@/theme/ThemeProvider';

interface Props {
  mode: 'signin' | 'signup';
  rememberMe?: boolean;
  onSuccess: (user: User) => void;
  onError: (message: string) => void;
}

/**
 * Real Google Identity Services button. Google returns a signed ID token which the backend verifies
 * (signature, audience, expiry) before creating or linking the account.
 */
export function GoogleSignInButton({ mode, rememberMe = false, onSuccess, onError }: Props) {
  const { config, loginWithGoogle } = useAuth();
  const { resolved } = useTheme();
  const [busy, setBusy] = useState(false);

  if (!config?.googleEnabled || !config.googleClientId) {
    return (
      <div className="space-y-1.5">
        <button
          type="button"
          disabled
          className="flex h-10 w-full cursor-not-allowed items-center justify-center gap-2 rounded-xl border border-slate-300 bg-white text-sm font-semibold text-slate-400 dark:border-gray-600 dark:bg-gray-800"
        >
          <GoogleIcon /> Continue with Google
        </button>
        <p className="text-center text-xs muted">Google sign-in is not configured on this server yet.</p>
      </div>
    );
  }

  return (
    <div className={busy ? 'pointer-events-none opacity-60' : undefined}>
      <div className="flex justify-center">
        <GoogleLogin
          key={resolved /* re-render the iframe button when the theme changes */}
          theme={resolved === 'dark' ? 'filled_black' : 'outline'}
          size="large"
          shape="pill"
          text={mode === 'signup' ? 'signup_with' : 'continue_with'}
          width="320"
          onSuccess={async (cred) => {
            if (!cred.credential) return onError('Google did not return a credential. Please try again.');
            setBusy(true);
            try {
              onSuccess(await loginWithGoogle(cred.credential, rememberMe));
            } catch (e) {
              onError(errorMessage(e));
            } finally {
              setBusy(false);
            }
          }}
          onError={() => onError('Google sign-in was cancelled or failed. Please try again.')}
        />
      </div>
    </div>
  );
}

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true">
      <path fill="#EA4335" d="M12 10.2v3.9h5.5c-.2 1.3-1.6 3.8-5.5 3.8-3.3 0-6-2.7-6-6.1s2.7-6.1 6-6.1c1.9 0 3.1.8 3.8 1.5l2.6-2.5C16.8 3.2 14.6 2.2 12 2.2 6.6 2.2 2.2 6.6 2.2 12s4.4 9.8 9.8 9.8c5.7 0 9.4-4 9.4-9.6 0-.6-.1-1.1-.2-1.6H12z" />
    </svg>
  );
}
