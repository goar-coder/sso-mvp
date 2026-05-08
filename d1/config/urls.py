from django.contrib import admin
from django.urls import path, include
from django.contrib.auth.decorators import login_required
from django.http import HttpResponse
from portal.views import KeycloakLogoutView

@login_required
def home(request):
    return HttpResponse(f"""
        <h1>D1 — App 1 (fuente de verdad)</h1>
        <p>Usuario: {request.user.username}</p>
        <form method="post" action="/oidc/logout/">
            <input type="hidden" name="csrfmiddlewaretoken" value="{request.META.get('CSRF_COOKIE', '')}">
            <button type="submit">Cerrar sesión</button>
        </form>
    """)

urlpatterns = [
    path('admin/', admin.site.urls),
    path('oidc/logout/', KeycloakLogoutView.as_view(), name='oidc_logout'),
    path('oidc/', include('mozilla_django_oidc.urls')),
    path('api/internal/', include('internal_auth.urls')),
    path('post-login/', include('portal.urls')),
    path('home/', home, name='home'),
    path('', home, name='root'),
]