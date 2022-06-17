resource "aws_security_group" "kafka_playground_sg" {
  name        = "newrelic_kafka_playground_sg"
  description = "Security group for the New Relic Kafka Playground.  Assuming that all ZooKeeper nodes, Kafka Brokers, and application hosts are in the same SG."

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "SSH"
  }

  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    self        = true
    description = "Internal security group. Allow everything."
  }
  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "udp"
    self        = true
    description = "Internal security group. Allow everything."
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  vpc_id = local.vpc_id

  tags = {
    owner_email = "avialpando@confluent.io"
    Name    = "newrelic_kafka_playground_sg"
    Project = var.project_name
  }
}