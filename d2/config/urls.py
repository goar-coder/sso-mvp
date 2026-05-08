from django.contrib import admin
from django.urls import path, include
from portal.views import KeycloakLogoutView, home

urlpatterns = [
    path('admin/', admin.site.urls),
    path('oidc/logout/', KeycloakLogoutView.as_view(), name='oidc_logout'),
    path('oidc/', include('mozilla_django_oidc.urls')),
    path('home/', home, name='home'),
    path('', home, name='root'),
]