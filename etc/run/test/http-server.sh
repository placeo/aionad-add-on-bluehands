set -x

############################
# car repair info addition
############################


curl -X POST http://192.168.10.9:8080/api/car-repair \
-H "Content-Type: application/json" \
-d '{
  "licensePlateNumber": "0012파0012",
  "carModel": "소나타",
  "repairStatus": "IN_PROGRESS",
  "estimatedFinishTime": "14:30:00"
}'

curl -X POST http://192.168.10.9:8080/api/car-repair \
-H "Content-Type: application/json" \
-d '{
  "licensePlateNumber": "0015타0015",
  "carModel": "X5",
  "repairStatus": "IN_PROGRESS",
  "estimatedFinishTime": "18:30:00"
}'

curl -X POST http://192.168.10.9:8080/api/car-repair \
-H "Content-Type: application/json" \
-d '{
  "licensePlateNumber": "0016하0016",
  "carModel": "르망",
  "repairStatus": "FINAL_INSPECTION",
  "estimatedFinishTime": "18:00:00"
}'

curl -X POST http://192.168.10.9:8080/api/car-repair \
-H "Content-Type: application/json" \
-d '{
  "licensePlateNumber": "0017보0017",
  "carModel": "E500",
  "repairStatus": "FINAL_INSPECTION",
  "estimatedFinishTime": "17:00:00"
}'


###########################
# all car repair info check
###########################

curl -X GET http://192.168.10.9:8080/api/car-repair


sleep 1

###########################
# specified car repair info check
###########################

curl -X GET http://192.168.10.9:8080/api/car-repair/004라444
curl -X GET http://192.168.10.9:8080/api/car-repair/007사777


###########################
# car repair info editing
###########################

curl -X PUT http://192.168.10.9:8080/api/car-repair/0012파0012 \
  -H "Content-Type: application/json" \
  -d '{
    "carModel": "소나타",
    "repairStatus": "COMPLETED",
    "estimatedFinishTime": null
  }'

curl -X PUT http://192.168.10.9:8080/api/car-repair/0010차100 \
  -H "Content-Type: application/json" \
  -d '{
    "carModel": "소나타",
    "repairStatus": "IN_PROGRESS",
    "estimatedFinishTime": "16:15:00"
  }'

###########################
# car repair info removal
###########################

curl -X DELETE http://192.168.10.9:8080/api/car-repair/005마555
curl -X DELETE http://192.168.10.9:8080/api/car-repair/003다333
curl -X DELETE http://192.168.10.9:8080/api/car-repair/002나222 
