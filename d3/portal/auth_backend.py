from mozilla_django_oidc.auth import OIDCAuthenticationBackend
import logging

logger = logging.getLogger(__name__)


class CustomOIDCBackend(OIDCAuthenticationBackend):

    def verify_claims(self, claims):
        return 'sub' in claims and 'preferred_username' in claims

    def get_username(self, claims):
        return claims.get('preferred_username')

    def filter_users_by_claims(self, claims):
        username = claims.get('preferred_username')
        if not username:
            return self.UserModel.objects.none()
        return self.UserModel.objects.filter(username=username)

    def create_user(self, claims):
        username = claims.get('preferred_username')
        return self.UserModel.objects.create_user(
            username=username,
            email=claims.get('email', ''),
            first_name=claims.get('given_name', ''),
            last_name=claims.get('family_name', ''),
        )

    def update_user(self, user, claims):
        return user

    def get_or_create_user(self, access_token, id_token, payload):
        logger.error(f"=== D2 PAYLOAD COMPLETO === {payload}")
        user = super().get_or_create_user(access_token, id_token, payload)
        if user and self.request:
            realm_access = payload.get('realm_access', {})
            all_roles = realm_access.get('roles', [])
            app_roles = [r for r in all_roles if r in ('d1-access', 'd2-access', 'd3-access')]
            self.request.session['oidc_app_roles'] = app_roles
        return user