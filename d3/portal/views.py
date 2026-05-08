from django.shortcuts import redirect
from django.contrib.auth import logout
from django.contrib.auth.decorators import login_required
from django.http import HttpResponse
from mozilla_django_oidc.views import OIDCLogoutView
from django.conf import settings
import urllib.parse


class KeycloakLogoutView(OIDCLogoutView):
    def post(self, request):
        id_token = request.session.get('oidc_id_token')
        logout(request)
        params = {
            'post_logout_redirect_uri': 'http://localhost:8001/oidc/authenticate/', 
        }
        if id_token:
            params['id_token_hint'] = id_token
        logout_url = settings.OIDC_OP_LOGOUT_ENDPOINT
        return redirect(f"{logout_url}?{urllib.parse.urlencode(params)}")


@login_required
def home(request):
    return HttpResponse(f"""
        <h1>D3 — App 3</h1>
        <p>Usuario: {request.user.username}</p>
        <form method="post" action="/oidc/logout/">
            <input type="hidden" name="csrfmiddlewaretoken" value="{request.META.get('CSRF_COOKIE', '')}">
            <button type="submit">Cerrar sesión</button>
        </form>
    """)