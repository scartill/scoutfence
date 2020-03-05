import argparse

message = """
{{
	"deveui":"11223345",
	"lat": 55.753,
	"lon": 37.556,
	"alarm": {alarm}
}}
"""

parser = argparse.ArgumentParser()
parser.add_argument('-a', dest='alarm', action='store_true', help="alarm")
args = parser.parse_args()

print(message.format(
    alarm='true' if args.alarm else 'false'
))

