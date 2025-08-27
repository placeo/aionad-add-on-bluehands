#curl -X POST http://172.23.245.114:8080/camera/capture
# curl -X GET http://172.23.245.114:8080/
# curl -X GET http://172.23.245.114:8080/status
curl -X POST http://172.23.249.90:8080/ticker -H "Content-Type: application/json" -d '{"text":"새로운 텍스트입니다!"}'
# curl -X POST http://172.23.245.114:8080/ticker -H "Content-Type: application/json" -d '{"text":"testing!!!"}'
# curl -X POST http://172.23.245.114:8080/ticker -H "Content-Type: application/json" -d '{"text":"Olive Young 올리브영 Olive Young"}'
