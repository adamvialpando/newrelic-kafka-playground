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

resource "aws_instance" "swarm_nodes" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.instance_type
  associate_public_ip_address = true
  key_name                    = var.key_name
  vpc_security_group_ids      = var.security_group_ids

  count = var.num_nodes

  tags = {
    Name          = "newrelic-kafka-playground-swarm-${count.index}"
    Role          = "swarm"
    swarm_manager = count.index == 0 ? "true" : "false"
    Project       = var.project_name
    DisplayName   = "swarm-${count.index}"
    owner_email   = "avialpando@confluent.io"
  }
  
  lifecycle {
    ignore_changes = [ami]
  }
}
