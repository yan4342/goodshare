from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/recommendations', methods=['GET'])
def get_recommendations():
    user_id = request.args.get('user_id')
    # In a real application, you would use the user_id to generate personalized recommendations.
    # Here, we'll just return some dummy data.
    recommendations = [
        {'product_id': 1, 'name': 'Product A', 'score': 0.9},
        {'product_id': 2, 'name': 'Product B', 'score': 0.8},
        {'product_id': 3, 'name': 'Product C', 'score': 0.7},
    ]
    return jsonify(recommendations)

if __name__ == '__main__':
    app.run(debug=True, port=5000)
