# nginx/certs — certificados TLS (bring-your-own)

nginx (prod) lee el par desde este directorio, montado read-only en `/etc/nginx/certs`:

- `fullchain.pem` — certificado + cadena intermedia.
- `privkey.pem`   — clave privada.

**Nada de esta carpeta se commitea** (ver `.gitignore`): sólo se versionan `.gitignore` y este README.

## Prod (certificado real del dominio del cliente)
Copiar el `fullchain.pem` y `privkey.pem` emitidos para el dominio (CA pública, wildcard del
hosting, o Let's Encrypt manual) a esta carpeta antes de levantar el stack. Permisos sugeridos:
`chmod 600 privkey.pem`.

## Dev / staging (placeholder self-signed)
```
bash scripts/gen-selfsigned-cert.sh            # CN=localhost
bash scripts/gen-selfsigned-cert.sh miapp.com  # CN + SAN del dominio
```
El navegador va a advertir (cert no confiable) — es esperado con self-signed. Sirve para
probar el pipeline TLS end-to-end antes de tener el cert real.
