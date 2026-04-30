from django.conf import settings
from django.http import JsonResponse


class InternalApiKeyMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        if request.path.startswith('/api/internal/'):
            api_key = request.headers.get('X-Internal-Api-Key')
            if api_key != settings.INTERNAL_API_KEY:
                return JsonResponse({'error': 'Unauthorized'}, status=401)
        return self.get_response(request)
