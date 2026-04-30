from django.contrib import admin
from django.urls import path, include
from django.contrib.auth.decorators import login_required
from django.http import HttpResponse

@login_required
def home(request):
    return HttpResponse(f"""
        <h1>D2 — App 2</h1>
        <p>Usuario: {request.user.username}</p>
        <a href="/oidc/logout/">Cerrar sesión</a>
    """)

urlpatterns = [
    path('admin/', admin.site.urls),
    path('oidc/', include('mozilla_django_oidc.urls')),
    path('home/', home, name='home'),
    path('', home, name='root'),
]