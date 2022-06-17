data "aws_vpc" "default" {
  default = true
}

data "aws_ami" "ubuntu" {
  most_recent = true

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-bionic-18.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  owners = ["099720109477"] # Canonical
}

resource "aws_instance" "zookeeper_nodes" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.zookeeper_instance_type
  associate_public_ip_address = true
  key_name                    = var.key_name
  vpc_security_group_ids      = var.security_group_ids

  count = var.zookeeper_num_nodes

  tags = {
    Name        = "${var.project_name}-zookeeper-${count.index}"
    Role        = "zookeeper-node"
    my_id       = "${count.index}"
    Project     = var.project_name
    DisplayName = "zookeeper-node-${count.index}"
    owner_email   = "avialpando@confluent.io"

  }
  
  lifecycle {
    ignore_changes = [ami]
  }
}

resource "aws_instance" "kafka_brokers" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.kafka_instance_type
  associate_public_ip_address = true
  key_name                    = var.key_name
  vpc_security_group_ids      = var.security_group_ids

  count = var.kafka_num_brokers

  tags = {
    Name        = "${var.project_name}-broker-${count.index}"
    Role        = "kafka-broker"
    broker_id   = "${count.index}"
    Project     = var.project_name
    DisplayName = "kafka-broker-${count.index}"
    owner_email   = "avialpando@confluent.io"

  }
  
  lifecycle {
    ignore_changes = [ami]
  }
}
