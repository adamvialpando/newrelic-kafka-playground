from locust import HttpUser, task, between

class MessageUser(HttpUser):
    wait_time = between(5, 20)

    @task
    def sendmessage(self):
        response = self.client.post("/sendmessage")
    
    @task
    def senderror(self):
        response = self.client.post("/error")
