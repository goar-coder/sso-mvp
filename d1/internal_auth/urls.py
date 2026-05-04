from django.urls import path
from . import views

urlpatterns = [
    path('auth/verify/', views.verify_user, name='internal-verify'),
    path('auth/user/', views.get_user, name='internal-get-user'),
    path('auth/verify-token/', views.verify_token, name='internal-verify-token'),
]
