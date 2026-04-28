import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";

export default function Navbar() {
  const { isAuthenticated, email, logout } = useAuth();
  const navigate = useNavigate();

  const onLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <header className="navbar">
      <h1>Stock Price Tracker</h1>
      {isAuthenticated && (
        <div>
          <span className="user">{email}</span>
          <button className="danger" onClick={onLogout}>Logout</button>
        </div>
      )}
    </header>
  );
}
