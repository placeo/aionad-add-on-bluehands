############################
# car repair info addition
############################


curl -X POST http://192.168.10.9:8080/api/car-repair \
-H "Content-Type: application/json" \
-d '{
  "licensePlateNumber": "12파12",
  "carModel": "소나타",
  "repairStatus": "IN_PROGRESS",
  "estimatedFinishTime": "14:30:00"
}'

curl -X POST http://192.168.10.9:8080/api/car-repair \
-H "Content-Type: application/json" \
-d '{
  "licensePlateNumber": "15타15",
  "carModel": "X5",
  "repairStatus": "IN_PROGRESS",
  "estimatedFinishTime": "18:30:00"
}'

curl -X POST http://192.168.10.9:8080/api/car-repair \
-H "Content-Type: application/json" \
-d '{
  "licensePlateNumber": "16하16",
  "carModel": "르망",
  "repairStatus": "FINAL_INSPECTION",
  "estimatedFinishTime": "18:00:00"
}'

curl -X POST http://192.168.10.9:8080/api/car-repair \
-H "Content-Type: application/json" \
-d '{
  "licensePlateNumber": "17보17",
  "carModel": "E500",
  "repairStatus": "FINAL_INSPECTION",
  "estimatedFinishTime": "17:00:00"
}'


###########################
# all car repair info check
###########################

curl -X GET http://192.168.10.9:8080/api/car-repair



###########################
# specified car repair info check
###########################

curl -X GET http://192.168.10.9:8080/api/car-repair/4라4
curl -X GET http://192.168.10.9:8080/api/car-repair/7사7


###########################
# car repair info editing
###########################

curl -X PUT http://192.168.10.9:8080/api/car-repair/12파12 \
  -H "Content-Type: application/json" \
  -d '{
    "carModel": "소나타",
    "repairStatus": "COMPLETED",
    "estimatedFinishTime": null
  }'

curl -X PUT http://192.168.10.9:8080/api/car-repair/10차10 \
  -H "Content-Type: application/json" \
  -d '{
    "carModel": "소나타",
    "repairStatus": "IN_PROGRESS",
    "estimatedFinishTime": "16:15:00"
  }'

###########################
# car repair info removal
###########################

curl -X DELETE http://192.168.10.9:8080/api/car-repair/5마5
curl -X DELETE http://192.168.10.9:8080/api/car-repair/3다3
curl -X DELETE http://192.168.10.9:8080/api/car-repair/2나2
