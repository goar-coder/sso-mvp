from django.shortcuts import render, redirect
from django.contrib.auth.decorators import login_required
from django.contrib.auth import logout
from mozilla_django_oidc.views import OIDCLogoutView
from django.conf import settings
import urllib.parse

APP_URLS = {
    'd1-access': 'http://localhost:8001/home/',
    'd2-access': 'http://localhost:8002/home/',
    'd3-access': 'http://localhost:8003/home/',
}


class KeycloakLogoutView(OIDCLogoutView):
    def post(self, request):
        id_token = request.session.get('oidc_id_token')
        logout(request)
        params = {
            'post_logout_redirect_uri': 'http://localhost:8001/',
        }
        if id_token:
            params['id_token_hint'] = id_token
        logout_url = settings.OIDC_OP_LOGOUT_ENDPOINT
        return redirect(f"{logout_url}?{urllib.parse.urlencode(params)}")


@login_required
def post_login(request):
    app_roles = request.session.get('oidc_app_roles', [])
    accessible = {
        role: url
        for role, url in APP_URLS.items()
        if role in app_roles
    }
    if len(accessible) == 0:
        return render(request, 'portal/no_access.html')
    if len(accessible) == 1:
        return redirect(list(accessible.values())[0])
    return render(request, 'portal/selector.html', {'apps': accessible})