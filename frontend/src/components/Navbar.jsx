import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";

function BrandLogo() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 17 L9 11 L13 15 L21 6" stroke="white" />
      <path d="M14 6 L21 6 L21 13" stroke="white" />
    </svg>
  );
}

function Avatar({ email }) {
  const initial = (email?.[0] || "?").toUpperCase();
  return <span className="avatar" aria-hidden="true">{initial}</span>;
}

export default function Navbar() {
  const { isAuthenticated, email, logout } = useAuth();
  const navigate = useNavigate();

  const onLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <header className="navbar">
      <Link to="/" className="brand" aria-label="Stock Price Tracker home">
        <span className="logo">
          <BrandLogo />
        </span>
        <span className="name">Stock Tracker</span>
      </Link>

      {isAuthenticated && (
        <div className="nav-right">
          <span className="user">
            <Avatar email={email} />
            {email}
          </span>
          <button className="ghost" onClick={onLogout}>Sign out</button>
        </div>
      )}
    </header>
  );
}
