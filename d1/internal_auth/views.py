import json
from django.contrib.auth import authenticate
from django.contrib.auth.models import User
from django.conf import settings
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods


def get_app_roles(user):
    """Convierte los grupos de Django en roles de app."""
    group_names = user.groups.values_list('name', flat=True)
    roles = []
    for group, role in settings.GROUP_TO_APP_ROLE.items():
        if group in group_names:
            roles.append(role)
    return roles


def user_to_dict(user):
    return {
        'id': str(user.pk),
        'username': user.username,
        'email': user.email,
        'first_name': user.first_name,
        'last_name': user.last_name,
        'is_active': user.is_active,
        'app_roles': get_app_roles(user),
    }


@csrf_exempt
@require_http_methods(['POST'])
def verify_user(request):
    """
    Keycloak llama aquí para verificar credenciales.
    POST /api/internal/auth/verify/
    Body: { "username": "...", "password": "..." }
    """
    try:
        body = json.loads(request.body)
        username = body.get('username', '').strip()
        password = body.get('password', '')
    except (json.JSONDecodeError, AttributeError):
        return JsonResponse({'valid': False}, status=400)

    if not username or not password:
        return JsonResponse({'valid': False})

    user = authenticate(request, username=username, password=password)

    if user is None or not user.is_active:
        return JsonResponse({'valid': False})

    return JsonResponse({
        'valid': True,
        'user': user_to_dict(user),
    })


@require_http_methods(['GET'])
def get_user(request):
    """
    Keycloak llama aquí para buscar un usuario por username.
    GET /api/internal/auth/user/?username=...
    """
    username = request.GET.get('username', '').strip()

    if not username:
        return JsonResponse({'found': False}, status=400)

    try:
        user = User.objects.get(username=username)
    except User.DoesNotExist:
        return JsonResponse({'found': False})

    return JsonResponse({'found': True, 'user': user_to_dict(user)})


@csrf_exempt
@require_http_methods(['POST'])
def verify_token(request):
    """
    Keycloak llama aquí para verificar un token de autenticación.
    POST /api/internal/auth/verify-token/
    Body: { "token": "..." }
    """
    try:
        body = json.loads(request.body)
        token = body.get('token', '').strip()
    except (json.JSONDecodeError, AttributeError):
        return JsonResponse({'valid': False}, status=400)

    if not token:
        return JsonResponse({'valid': False})

    # Aquí deberías implementar la lógica para validar el token
    # Por ahora, como ejemplo, asumiremos que el token es el username con un prefijo
    # En una implementación real, esto podría ser un JWT, UUID en BD, etc.
    if token.startswith('token_'):
        username = token.replace('token_', '')
        try:
            user = User.objects.get(username=username, is_active=True)
            return JsonResponse({
                'valid': True,
                'user': user_to_dict(user),
            })
        except User.DoesNotExist:
            pass

    return JsonResponse({'valid': False})
