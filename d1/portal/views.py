from django.shortcuts import render, redirect
from django.contrib.auth.decorators import login_required

APP_URLS = {
    'd1-access': 'http://localhost:8001/home/',
    'd2-access': 'http://localhost:8002/home/',
    'd3-access': 'http://localhost:8003/home/',
}

@login_required
def post_login(request):
    # Leer roles del token OIDC guardados en sesión
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
