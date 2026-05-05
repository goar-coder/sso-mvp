from decouple import config
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
SECRET_KEY = config('DJANGO_SECRET_KEY', default='dev-secret-key-d1')
DEBUG = True
ALLOWED_HOSTS = ['*']

INSTALLED_APPS = [
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'rest_framework',
    'mozilla_django_oidc',
    'internal_auth',
    'portal',
]

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
    'internal_auth.middleware.InternalApiKeyMiddleware', # el nuevo middleware para la API interna
]

# mapeo de los grupos de Keycloak a roles de la app
GROUP_TO_APP_ROLE = {
    'app-d1': 'd1-access',
    'app-d2': 'd2-access',
    'app-d3': 'd3-access',
}

ROOT_URLCONF = 'config.urls'

TEMPLATES = [{
    'BACKEND': 'django.template.backends.django.DjangoTemplates',
    'DIRS': [BASE_DIR / 'templates'],
    'APP_DIRS': True,
    'OPTIONS': {
        'context_processors': [
            'django.template.context_processors.request',
            'django.contrib.auth.context_processors.auth',
            'django.contrib.messages.context_processors.messages',
        ],
    },
}]

WSGI_APPLICATION = 'config.wsgi.application'

DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.mysql',
        'NAME': config('DB_NAME'),
        'USER': config('DB_USER'),
        'PASSWORD': config('DB_PASSWORD'),
        'HOST': config('DB_HOST', default='mysql'),
        'PORT': config('DB_PORT', default='3306'),
    }
}

STATIC_URL = '/static/'
STATIC_ROOT = BASE_DIR / 'staticfiles'
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

# ── Auth backends ──────────────────────────────────────────────────────
# ModelBackend primero → /admin/ funciona con Django nativo
# OIDCBackend segundo  → el resto de la app usa Keycloak
AUTHENTICATION_BACKENDS = [
    'django.contrib.auth.backends.ModelBackend',
    'mozilla_django_oidc.auth.OIDCAuthenticationBackend',
]

# ── OIDC ──────────────────────────────────────────────────────────────
KC_BASE = config('KEYCLOAK_URL', default='http://keycloak:8080')
KC_REALM = f'{KC_BASE}/realms/django-realm/protocol/openid-connect'
KC_PUBLIC_BASE = 'http://localhost:8080'
KC_PUBLIC_REALM = f'{KC_PUBLIC_BASE}/realms/django-realm/protocol/openid-connect'

OIDC_RP_CLIENT_ID     = config('OIDC_RP_CLIENT_ID')
OIDC_RP_CLIENT_SECRET = config('OIDC_RP_CLIENT_SECRET')
OIDC_RP_SIGN_ALGO     = 'RS256'

OIDC_OP_JWKS_ENDPOINT          = f'{KC_REALM}/certs'
OIDC_OP_AUTHORIZATION_ENDPOINT = f'{KC_PUBLIC_REALM}/auth'  # navegador → localhost
OIDC_OP_TOKEN_ENDPOINT         = f'{KC_REALM}/token'        # server → keycloak
OIDC_OP_USER_ENDPOINT          = f'{KC_REALM}/userinfo'     # server → keycloak
OIDC_OP_LOGOUT_ENDPOINT        = f'{KC_PUBLIC_REALM}/logout'

LOGIN_URL           = '/oidc/authenticate/'
LOGIN_REDIRECT_URL  = '/post-login/'
LOGOUT_REDIRECT_URL = '/'

OIDC_CREATE_USER = True

# Callback personalizado para capturar roles
def oidc_callback_with_roles(strategy, details, backend, user, *args, **kwargs):
    """Callback para guardar roles de Keycloak en la sesión."""
    request = strategy.request
    if hasattr(request, 'session') and 'access_token' in kwargs.get('response', {}):
        # Los roles vienen en el token de acceso o userinfo
        # Por ahora simulamos basado en grupos del usuario Django
        from django.conf import settings
        roles = []
        for group_name in user.groups.values_list('name', flat=True):
            if group_name in settings.GROUP_TO_APP_ROLE:
                roles.append(settings.GROUP_TO_APP_ROLE[group_name])
        request.session['oidc_app_roles'] = roles

# Registrar el callback
OIDC_RP_CALLBACK_FUNC = 'config.settings.oidc_callback_with_roles'

LOGGING = {
    'version': 1,
    'handlers': {
        'console': {
            'class': 'logging.StreamHandler',
        },
    },
    'root': {
        'handlers': ['console'],
        'level': 'INFO',
    },
}

# ── API key interna (Keycloak SPI → D1) ───────────────────────────────
INTERNAL_API_KEY = config('INTERNAL_API_KEY')