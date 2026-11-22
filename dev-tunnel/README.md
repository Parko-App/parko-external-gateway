# Túnel de desarrollo (ngrok + proxy)

Para probar **en local** el pago de un ticket de visitante y las cargas de saldo con Mercado Pago.

## Por qué hace falta

Mercado Pago avisa que un pago se acreditó llamando a `POST /webhooks/mercadopago` del gateway, y el QR del ticket
de un visitante abre `GET /api/v1/access/sessions/<id>/pay` de access-service. Las dos rutas tienen que ser públicas,
pero un dominio de ngrok solo reenvía a un puerto. Si el túnel apunta a access-service, el webhook nunca llega al
gateway: el pago se cobra en Mercado Pago pero el ticket queda `PENDING_PAYMENT` y la carga de saldo queda pendiente.

Este proxy resuelve eso con un solo dominio:

```
Internet -> ngrok -> caddy -> /webhooks/mercadopago                 -> external-gateway :8085
                           -> /api/v1/access/sessions/<id>/pay      -> access-service   :8081
                           -> cualquier otra ruta                   -> 404
```

El resto de la API de access-service **no** queda expuesta a internet.

## Uso

1. Tené `NGROK_AUTHTOKEN` y `NGROK_DOMAIN` (el dominio reservado en esa misma cuenta, sin `https://`) en el entorno o
   en un `.env` dentro de esta carpeta (está ignorado por git).
2. Levantá el túnel:
   ```powershell
   cd dev-tunnel
   docker compose up -d
   ```
3. Levantá los servicios **sin** abrir su propio túnel, que usan el mismo dominio y chocarían con este:

   | Servicio | Variables |
   |---|---|
   | access-service | `NGROK_ENABLED=false` y `ACCESS_SERVICE_PUBLIC_URL=https://<NGROK_DOMAIN>` |
   | external-gateway | `NGROK_ENABLED=false` (es el valor por defecto) y `MERCADOPAGO_NOTIFICATION_URL=https://<NGROK_DOMAIN>/webhooks/mercadopago` |

   `ACCESS_SERVICE_PUBLIC_URL` define la URL que lleva el QR del ticket. `MERCADOPAGO_NOTIFICATION_URL` es el
   `notification_url` con el que el gateway crea cada preferencia de pago: sin ella Mercado Pago no tiene a dónde avisar.
   `MERCADOPAGO_WEBHOOK_SECRET` tiene que ser el secreto del webhook de la aplicación de Mercado Pago.
4. Para apagarlo: `docker compose down`.

## Comprobar que anda

```powershell
# con el id de un ticket pendiente real: 302 hacia Mercado Pago (con uno inexistente: 404 de access-service)
curl.exe -i https://<NGROK_DOMAIN>/api/v1/access/sessions/<id-de-sesion>/pay

# llega al gateway: 401 o 400 porque faltan los headers de firma (lo que no debe dar es el 404 del proxy)
curl.exe -i -X POST "https://<NGROK_DOMAIN>/webhooks/mercadopago?data.id=1"

# cualquier otra ruta: 404 del proxy (cuerpo "No encontrado")
curl.exe -i https://<NGROK_DOMAIN>/api/v1/access
```

## Cosas a tener en cuenta

- Un token de Mercado Pago de producción (`APP_USR-`) cobra plata real, aunque el ticket sea de prueba.
- Si el pago se cobra pero el ticket no cambia, mirá si llegó el aviso: `docker logs parko-dev-proxy` y el log del gateway
  (`Notificacion de Mercado Pago recibida`).
- En producción el gateway es público y esta carpeta no se usa.
