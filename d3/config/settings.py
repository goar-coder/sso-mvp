from decouple import config
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
SECRET_KEY = config('DJANGO_SECRET_KEY', default='dev-secret-key-d3')
DEBUG = True
ALLOWED_HOSTS = ['*']

INSTALLED_APPS = [
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'mozilla_django_oidc',
    'portal',  # ← añadir
]

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

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
AUTHENTICATION_BACKENDS = [
    'django.contrib.auth.backends.ModelBackend',
    'portal.auth_backend.CustomOIDCBackend',  # ← custom backend
]

# ── OIDC ──────────────────────────────────────────────────────────────
OIDC_RP_CLIENT_ID     = config('OIDC_RP_CLIENT_ID')
OIDC_RP_CLIENT_SECRET = config('OIDC_RP_CLIENT_SECRET')
OIDC_RP_SIGN_ALGO     = 'RS256'

OIDC_OP_JWKS_ENDPOINT          = 'http://localhost:8080/realms/django-realm/protocol/openid-connect/certs'
OIDC_OP_AUTHORIZATION_ENDPOINT = 'http://localhost:8080/realms/django-realm/protocol/openid-connect/auth'
OIDC_OP_TOKEN_ENDPOINT         = 'http://localhost:8080/realms/django-realm/protocol/openid-connect/token'
OIDC_OP_USER_ENDPOINT          = 'http://localhost:8080/realms/django-realm/protocol/openid-connect/userinfo'
OIDC_OP_LOGOUT_ENDPOINT        = 'http://localhost:8080/realms/django-realm/protocol/openid-connect/logout'

OIDC_OP_ISSUER = 'http://localhost:8080/realms/django-realm'

LOGIN_URL           = '/oidc/authenticate/'
LOGIN_REDIRECT_URL  = '/home/'
LOGOUT_REDIRECT_URL = '/'

OIDC_CREATE_USER = True